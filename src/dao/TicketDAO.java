package dao;

import database.DBConnection;
import java.sql.*;
import java.util.*;

public class TicketDAO {
    public List<Integer> findOpenTicketIds() throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String q = "SELECT id FROM tickets WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS') ORDER BY created_at";
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

    public List<Map<String, Object>> findByReporter(int reporterId) throws SQLException {
        List<Map<String, Object>> tickets = new ArrayList<>();
        String q = """
                SELECT t.id, t.status, t.created_at, i.title, i.category, i.location, i.priority
                FROM tickets t
                JOIN issues i ON i.id = t.issue_id
                WHERE t.reported_by = ?
                ORDER BY t.created_at DESC
                """;

        try (Connection c = DBConnection.getConnection();
             PreparedStatement s = c.prepareStatement(q)) {
            s.setInt(1, reporterId);
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
                    t.created_at ASC
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

}
