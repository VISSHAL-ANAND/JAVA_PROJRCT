package dao;

import database.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BookingDAO {
    private static final int BOOKING_LOCK_NAMESPACE = 1128351312;

    public int createIfAvailable(int resourceId, int userId, LocalDateTime start, LocalDateTime end) throws SQLException {
        String resourceSql = "SELECT quantity, available FROM resources WHERE id = ?";
        String overlapSql = "SELECT COUNT(*) FROM bookings WHERE resource_id = ? AND start_time < ? AND end_time > ?";
        String insertSql = "INSERT INTO bookings(resource_id,booked_by,start_time,end_time) VALUES(?,?,?,?)";

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Serialize booking attempts for this resource without requiring UPDATE on resources.
                try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
                    lock.setInt(1, BOOKING_LOCK_NAMESPACE);
                    lock.setInt(2, resourceId);
                    lock.execute();
                }

                int capacity;
                try (PreparedStatement statement = connection.prepareStatement(resourceSql)) {
                    statement.setInt(1, resourceId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Resource not found");
                        if (!rs.getBoolean("available")) throw new IllegalArgumentException("Resource is unavailable");
                        capacity = rs.getInt("quantity");
                    }
                }
                if (capacity <= 0) throw new IllegalArgumentException("Resource has no available capacity");

                int overlappingBookings;
                try (PreparedStatement statement = connection.prepareStatement(overlapSql)) {
                    statement.setInt(1, resourceId);
                    statement.setTimestamp(2, Timestamp.valueOf(end));
                    statement.setTimestamp(3, Timestamp.valueOf(start));
                    try (ResultSet rs = statement.executeQuery()) {
                        rs.next();
                        overlappingBookings = rs.getInt(1);
                    }
                }
                if (overlappingBookings >= capacity) {
                    throw new SQLException("Resource is already booked for that time.", "23P01");
                }

                try (PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, resourceId);
                    statement.setInt(2, userId);
                    statement.setTimestamp(3, Timestamp.valueOf(start));
                    statement.setTimestamp(4, Timestamp.valueOf(end));
                    statement.executeUpdate();
                    try (ResultSet rs = statement.getGeneratedKeys()) {
                        if (rs.next()) {
                            int id = rs.getInt(1);
                            connection.commit();
                            return id;
                        }
                    }
                }
                throw new SQLException("Unable to create booking");
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sqlException) throw sqlException;
                if (e instanceof RuntimeException runtimeException) throw runtimeException;
                throw new SQLException("Unable to create booking", e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<Map<String, Object>> findByUser(int userId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql = """
                SELECT b.id, b.resource_id, r.name AS resource_name, r.type,
                       b.start_time, b.end_time
                FROM bookings b
                JOIN resources r ON r.id = b.resource_id
                WHERE b.booked_by = ?
                ORDER BY b.start_time DESC
                LIMIT 100
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("resourceId", rs.getInt("resource_id"));
                    item.put("resourceName", rs.getString("resource_name"));
                    item.put("type", rs.getString("type"));
                    item.put("startTime", rs.getTimestamp("start_time").toString());
                    item.put("endTime", rs.getTimestamp("end_time").toString());
                    items.add(item);
                }
            }
        }
        return items;
    }

    public boolean cancelFutureBooking(int bookingId, int userId) throws SQLException {
        String sql = "DELETE FROM bookings WHERE id = ? AND booked_by = ? AND start_time > CURRENT_TIMESTAMP";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            statement.setInt(2, userId);
            return statement.executeUpdate() == 1;
        }
    }

}
