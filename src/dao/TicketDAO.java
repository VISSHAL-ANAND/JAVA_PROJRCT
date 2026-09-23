package dao;

import database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TicketDAO {
    public List<Integer> findOpenTicketIds() throws SQLException {
        String sql = "SELECT id FROM tickets WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS') ORDER BY created_at";
        List<Integer> ids = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt("id"));
        }
        return ids;
    }

    public int createTicket(int issueId, int reporterId) throws SQLException {
        String sql = "INSERT INTO tickets (issue_id, reported_by, status) VALUES (?, ?, 'OPEN')";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, issueId);
            statement.setInt(2, reporterId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Unable to create ticket");
    }

    public void assignTechnician(int ticketId, int technicianId) throws SQLException {
        String sql = "UPDATE tickets SET assigned_technician_id = ?, status = 'ASSIGNED' WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, technicianId);
            statement.setInt(2, ticketId);
            statement.executeUpdate();
        }
    }
}
