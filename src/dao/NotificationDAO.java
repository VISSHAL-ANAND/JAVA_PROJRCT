package dao;

import database.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    public List<String> findUnreadMessages(int userId) throws SQLException {
        String sql = "SELECT message FROM notifications WHERE user_id = ? AND is_read = FALSE ORDER BY created_at DESC";
        List<String> messages = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    messages.add(rs.getString("message"));
                }
            }
        }
        return messages;
    }
}
