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
}
