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
        // Booking intervals are half-open [start, end). Occupancy can only increase
        // at the requested start or at an existing booking's start within the interval.
        // Evaluate those points so sequential bookings are not mistaken for simultaneous use.
        String peakOccupancySql = """
                WITH points AS (
                    SELECT ? AS point_time
                    UNION
                    SELECT start_time
                    FROM bookings
                    WHERE resource_id = ? AND start_time > ? AND start_time < ?
                )
                SELECT COALESCE(MAX((
                    SELECT COUNT(*)
                    FROM bookings b
                    WHERE b.resource_id = ?
                      AND b.start_time <= points.point_time
                      AND b.end_time > points.point_time
                )), 0)
                FROM points
                """;
        String insertSql = "INSERT INTO bookings(resource_id,booked_by,start_time,end_time) VALUES(?,?,?,?)";

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockResourceForBooking(connection, resourceId);

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

                int peakOccupancy;
                try (PreparedStatement statement = connection.prepareStatement(peakOccupancySql)) {
                    statement.setTimestamp(1, Timestamp.valueOf(start));
                    statement.setInt(2, resourceId);
                    statement.setTimestamp(3, Timestamp.valueOf(start));
                    statement.setTimestamp(4, Timestamp.valueOf(end));
                    statement.setInt(5, resourceId);
                    try (ResultSet rs = statement.executeQuery()) {
                        rs.next();
                        peakOccupancy = rs.getInt(1);
                    }
                }
                if (peakOccupancy >= capacity) {
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

    private void lockResourceForBooking(Connection connection, int resourceId) throws SQLException {
        // PostgreSQL's least-privilege app role intentionally has no UPDATE privilege
        // on resources, which PostgreSQL requires for SELECT ... FOR UPDATE. A
        // transaction-scoped advisory lock gives equivalent per-resource serialization.
        if ("PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
            try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
                lock.setInt(1, BOOKING_LOCK_NAMESPACE);
                lock.setInt(2, resourceId);
                lock.execute();
            }
            return;
        }

        // MySQL uses a row lock, held until this transaction commits or rolls back.
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT id FROM resources WHERE id = ? FOR UPDATE")) {
            lock.setInt(1, resourceId);
            try (ResultSet rs = lock.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Resource not found");
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
                ORDER BY b.start_time DESC, b.id DESC
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
