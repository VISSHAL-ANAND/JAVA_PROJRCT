package dao;

import database.DBConnection;
import java.sql.*;
import java.time.LocalDateTime;

public class BookingDAO {
    public int createIfAvailable(int resourceId, int userId, LocalDateTime start, LocalDateTime end) throws SQLException {
        String q = "SELECT 1 FROM bookings WHERE resource_id=? AND start_time<? AND end_time>? LIMIT 1 FOR UPDATE";
        String ins = "INSERT INTO bookings(resource_id,booked_by,start_time,end_time) VALUES(?,?,?,?)";

        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement(q)) {
                    s.setInt(1, resourceId);
                    s.setTimestamp(2, Timestamp.valueOf(end));
                    s.setTimestamp(3, Timestamp.valueOf(start));

                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) {
                            throw new SQLException("Resource is already booked for that time.");
                        }
                    }
                }

                try (PreparedStatement s = c.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                    s.setInt(1, resourceId);
                    s.setInt(2, userId);
                    s.setTimestamp(3, Timestamp.valueOf(start));
                    s.setTimestamp(4, Timestamp.valueOf(end));
                    s.executeUpdate();

                    try (ResultSet r = s.getGeneratedKeys()) {
                        if (r.next()) {
                            int id = r.getInt(1);
                            c.commit();
                            return id;
                        }
                    }
                }

                throw new SQLException("Unable to create booking");
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException s) {
                    throw s;
                }
                throw new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
