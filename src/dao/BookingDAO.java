package dao;

import database.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;

public class BookingDAO {
    public boolean hasConflict(int resourceId, LocalDateTime start, LocalDateTime end) throws SQLException {
        String sql = """
                SELECT 1 FROM bookings
                WHERE resource_id = ?
                  AND start_time < ?
                  AND end_time > ?
                LIMIT 1
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, resourceId);
            statement.setTimestamp(2, Timestamp.valueOf(end));
            statement.setTimestamp(3, Timestamp.valueOf(start));

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int createBooking(int resourceId, int userId,
                             LocalDateTime start, LocalDateTime end) throws SQLException {
        String sql = "INSERT INTO bookings (resource_id, booked_by, start_time, end_time) VALUES (?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, resourceId);
            statement.setInt(2, userId);
            statement.setTimestamp(3, Timestamp.valueOf(start));
            statement.setTimestamp(4, Timestamp.valueOf(end));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        throw new SQLException("Unable to create booking");
    }
}
