package dao;

import database.DBConnection;
import java.sql.*;
import java.time.LocalDateTime;

public class BookingDAO {

    public boolean hasConflict(int resourceId, LocalDateTime start, LocalDateTime end)
            throws SQLException {

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
}
