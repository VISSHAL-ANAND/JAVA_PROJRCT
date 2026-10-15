package dao;

import database.DBConnection;
import java.sql.*;
import java.util.*;

public class TicketDAO {
    public List<Integer> findOpenTicketIds() throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String q = "SELECT id FROM tickets WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS') ORDER BY created_at, id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement s = c.prepareStatement(q);
             ResultSet r = s.executeQuery()) {
            while (r.next()) ids.add(r.getInt(1));
        }
        return ids;
    }

    public int createTicket(int issueId, int reporterId) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            return createTicket(c, issueId, reporterId);
        }
    }

    public int createTicket(Connection c, int issueId, int reporterId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO tickets(issue_id,reported_by,status) VALUES(?,?,'OPEN')",
                Statement.RETURN_GENERATED_KEYS)) {
            s.setInt(1, issueId);
            s.setInt(2, reporterId);
            s.executeUpdate();
            try (ResultSet r = s.getGeneratedKeys()) {
                if (r.next()) return r.getInt(1);
            }
        }
        throw new SQLException("Unable to create ticket");
    }

    public void assignTechnician(int ticketId, int technicianId) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            assignTechnician(c, ticketId, technicianId);
        }
    }

    public void assignTechnician(Connection c, int ticketId, int technicianId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE tickets SET assigned_technician_id=?,status='ASSIGNED' WHERE id=? AND status='OPEN'")) {
            s.setInt(1, technicianId);
            s.setInt(2, ticketId);
            if (s.executeUpdate() != 1) throw new SQLException("Ticket could not be assigned");
        }
    }

    private static final int REPORTER_TICKET_PAGE_SIZE = 20;

    /** Backwards-compatible bounded first-page lookup. */
    public List<Map<String, Object>> findByReporter(int reporterId) throws SQLException {
        return findByReporter(reporterId, REPORTER_TICKET_PAGE_SIZE, 0L);
    }

    public List<Map<String, Object>> findByReporter(int reporterId, int limit, long offset) throws SQLException {
        if (reporterId <= 0 || limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid reporter ticket page");
        }
        List<Map<String, Object>> tickets = new ArrayList<>();
        String q = """
                SELECT t.id, t.status, t.created_at, i.title, i.category, i.location, i.priority
                FROM tickets t
                JOIN issues i ON i.id = t.issue_id
                WHERE t.reported_by = ?
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT ? OFFSET ?
                """;

        try (Connection c = DBConnection.getConnection();
             PreparedStatement s = c.prepareStatement(q)) {
            s.setInt(1, reporterId);
            s.setInt(2, limit);
            s.setLong(3, offset);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> ticket = new LinkedHashMap<>();
                    ticket.put("id", r.getInt("id"));
                    ticket.put("title", r.getString("title"));
                    ticket.put("category", r.getString("category"));
                    ticket.put("location", r.getString("location"));
                    ticket.put("priority", r.getString("priority"));
                    ticket.put("status", r.getString("status"));
                    ticket.put("createdAt", r.getTimestamp("created_at").toString());
                    tickets.add(ticket);
                }
            }
        }
        return tickets;
    }

    /** Counts stay correct regardless of which page is being viewed. */
    public Map<String, Integer> getReporterTicketSummary(int reporterId) throws SQLException {
        String q = """
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN status IN ('OPEN','ASSIGNED','IN_PROGRESS') THEN 1 ELSE 0 END) AS open_count,
                       SUM(CASE WHEN status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS resolved_count
                FROM tickets WHERE reported_by = ?
                """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement s = c.prepareStatement(q)) {
            s.setInt(1, reporterId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new SQLException("Unable to summarize reporter tickets");
                Map<String, Integer> summary = new LinkedHashMap<>();
                summary.put("total", r.getInt("total"));
                summary.put("openCount", r.getInt("open_count"));
                summary.put("resolvedCount", r.getInt("resolved_count"));
                return summary;
            }
        }
    }

    public List<Map<String, Object>> findAssignedTo(int userId, boolean includeAll) throws SQLException {
        List<Map<String, Object>> tickets = new ArrayList<>();
        String filter = includeAll ? "" : "WHERE t.assigned_technician_id = ?";
        String q = """
                SELECT t.id, t.status, t.created_at, t.reported_by, t.assigned_technician_id,
                       i.title, i.category, i.location, i.priority
                FROM tickets t
                JOIN issues i ON i.id = t.issue_id
                %s
                ORDER BY CASE i.priority
                    WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                    t.created_at ASC, t.id ASC
                LIMIT 100
                """.formatted(filter);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(q)) {
            if (!includeAll) statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> ticket = new LinkedHashMap<>();
                    ticket.put("id", rs.getInt("id"));
                    ticket.put("title", rs.getString("title"));
                    ticket.put("category", rs.getString("category"));
                    ticket.put("location", rs.getString("location"));
                    ticket.put("priority", rs.getString("priority"));
                    ticket.put("status", rs.getString("status"));
                    ticket.put("reporterId", rs.getInt("reported_by"));
                    ticket.put("assignedTechnicianId", (Integer) rs.getObject("assigned_technician_id"));
                    ticket.put("createdAt", rs.getTimestamp("created_at").toString());
                    tickets.add(ticket);
                }
            }
        }
        return tickets;
    }

    public void updateStatus(int ticketId, int actorId, String actorRole, String nextStatus) throws SQLException {
        if (ticketId <= 0 || actorId <= 0 || nextStatus == null ||
                !Set.of("IN_PROGRESS", "RESOLVED", "CLOSED").contains(nextStatus)) {
            throw new IllegalArgumentException("Invalid ticket status update");
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String currentStatus;
                int reporterId;
                Integer assignedId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status, reported_by, assigned_technician_id FROM tickets WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, ticketId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw new java.util.NoSuchElementException("Ticket not found");
                        currentStatus = rs.getString("status");
                        reporterId = rs.getInt("reported_by");
                        assignedId = (Integer) rs.getObject("assigned_technician_id");
                    }
                }

                boolean admin = "ADMIN".equals(actorRole);
                boolean staff = "MAINTENANCE".equals(actorRole) || "TECHNICIAN".equals(actorRole);
                if (!admin && (!staff || assignedId == null || assignedId != actorId)) {
                    throw new SecurityException("Ticket is not assigned to this user");
                }
                if (!service.TicketStatusRules.canTransition(currentStatus, nextStatus)) {
                    throw new IllegalStateException("Invalid ticket status transition");
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE tickets SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                    statement.setString(1, nextStatus);
                    statement.setInt(2, ticketId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ticket_history(ticket_id, changed_by, previous_status, new_status) VALUES(?,?,?,?)")) {
                    statement.setInt(1, ticketId);
                    statement.setInt(2, actorId);
                    statement.setString(3, currentStatus);
                    statement.setString(4, nextStatus);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO notifications(user_id, message) VALUES(?,?)")) {
                    statement.setInt(1, reporterId);
                    statement.setString(2, "Ticket #" + ticketId + " status changed to " + nextStatus + ".");
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sqlException) throw sqlException;
                if (e instanceof RuntimeException runtimeException) throw runtimeException;
                throw new SQLException("Unable to update ticket status", e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


    public void assignTicketToStaff(int ticketId, int staffId, int adminId) throws SQLException {
        if (ticketId <= 0 || staffId <= 0 || adminId <= 0) {
            throw new IllegalArgumentException("Invalid ticket assignment");
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int reporterId;
                String status;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status, reported_by FROM tickets WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, ticketId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw new java.util.NoSuchElementException("Ticket not found");
                        status = rs.getString("status");
                        reporterId = rs.getInt("reported_by");
                    }
                }
                if (!"OPEN".equals(status)) throw new IllegalStateException("Only open tickets can be assigned");

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT role, available FROM users WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, staffId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Staff member not found");
                        String role = rs.getString("role");
                        if (!"MAINTENANCE".equals(role) && !"TECHNICIAN".equals(role)) {
                            throw new IllegalArgumentException("Selected user is not assignable staff");
                        }
                        if (!rs.getBoolean("available")) throw new IllegalArgumentException("Selected staff member is unavailable");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE tickets SET assigned_technician_id = ?, status = 'ASSIGNED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'OPEN'")) {
                    statement.setInt(1, staffId);
                    statement.setInt(2, ticketId);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("Ticket assignment conflict");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ticket_history(ticket_id, changed_by, previous_status, new_status) VALUES(?,?,?,?)")) {
                    statement.setInt(1, ticketId);
                    statement.setInt(2, adminId);
                    statement.setString(3, "OPEN");
                    statement.setString(4, "ASSIGNED");
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO notifications(user_id, message) VALUES(?,?), (?,?)")) {
                    statement.setInt(1, staffId);
                    statement.setString(2, "Ticket #" + ticketId + " has been assigned to you.");
                    statement.setInt(3, reporterId);
                    statement.setString(4, "Ticket #" + ticketId + " has been assigned to a staff member.");
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sqlException) throw sqlException;
                if (e instanceof RuntimeException runtimeException) throw runtimeException;
                throw new SQLException("Unable to assign ticket", e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


    public void recordStatusChange(Connection connection, int ticketId, Integer changedBy,
                                   String previousStatus, String newStatus) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ticket_history(ticket_id, changed_by, previous_status, new_status) VALUES(?,?,?,?)")) {
            statement.setInt(1, ticketId);
            if (changedBy == null) statement.setNull(2, java.sql.Types.INTEGER);
            else statement.setInt(2, changedBy);
            statement.setString(3, previousStatus);
            statement.setString(4, newStatus);
            statement.executeUpdate();
        }
    }

    public List<Map<String, Object>> findHistory(int ticketId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql = """
                SELECT h.previous_status, h.new_status, h.created_at, u.name AS changed_by_name
                FROM ticket_history h
                LEFT JOIN users u ON u.id = h.changed_by
                WHERE h.ticket_id = ?
                ORDER BY h.created_at ASC, h.id ASC
                LIMIT 100
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("fromStatus", rs.getString("previous_status"));
                    item.put("toStatus", rs.getString("new_status"));
                    item.put("changedBy", rs.getString("changed_by_name") == null ? "CAMPUSOS system" : rs.getString("changed_by_name"));
                    item.put("createdAt", rs.getTimestamp("created_at").toString());
                    items.add(item);
                }
            }
        }
        return items;
    }

    public boolean canViewTicket(int ticketId, int userId, String role) throws SQLException {
        String sql = "SELECT 1 FROM tickets WHERE id = ? AND (? = 'ADMIN' OR reported_by = ? OR assigned_technician_id = ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            statement.setString(2, role);
            statement.setInt(3, userId);
            statement.setInt(4, userId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

}
