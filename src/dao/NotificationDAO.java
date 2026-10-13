package dao;

import database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {
    public List<String> findUnreadMessages(int userId) throws SQLException {
        String sql = "SELECT message FROM notifications WHERE user_id = ? AND is_read = FALSE ORDER BY created_at";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);

            try (ResultSet rs = statement.executeQuery()) {
                List<String> messages = new ArrayList<>();
                while (rs.next()) messages.add(rs.getString("message"));
                return messages;
            }
        }
    }

    public void createNotification(int userId, String message) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, message) VALUES (?, ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, message);
            statement.executeUpdate();
        }
    }

    public void markAllRead(int userId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }

    public List<java.util.Map<String, Object>> findByUser(int userId) throws SQLException {
        List<java.util.Map<String, Object>> items = new ArrayList<>();
        String sql = "SELECT id, message, is_read, created_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 100";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("message", rs.getString("message"));
                    item.put("read", rs.getBoolean("is_read"));
                    item.put("createdAt", rs.getTimestamp("created_at").toString());
                    items.add(item);
                }
            }
        }
        return items;
    }

    public boolean markRead(int userId, int notificationId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE id = ? AND user_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, notificationId);
            statement.setInt(2, userId);
            return statement.executeUpdate() == 1;
        }
    }

}
