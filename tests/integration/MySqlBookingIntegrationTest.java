package integration;

import dao.BookingDAO;
import database.DBConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_MYSQL_IT_ENABLED", matches = "true")
class MySqlBookingIntegrationTest {
    @Test
    void mysqlBookingCapacityAndConcurrencyWorkEndToEnd() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int userId = -1;
        int resourceId = -1;
        try {
            try (Connection connection = openAdminConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO users(name,email,password,role) VALUES(?,?,?,'STUDENT')",
                         Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, "MySQL IT " + suffix);
                statement.setString(2, "mysql-it-" + suffix + "@example.test");
                statement.setString(3, "test-only-not-a-real-password-hash");
                statement.executeUpdate();
                try (ResultSet rs = statement.getGeneratedKeys()) {
                    assertTrue(rs.next(), "MySQL must return the generated user ID");
                    userId = rs.getInt(1);
                }
            }

            try (Connection connection = openAdminConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO resources(name,type,quantity,available) VALUES(?,'ROOM',1,TRUE)",
                         Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, "MySQL IT Room " + suffix);
                statement.executeUpdate();
                try (ResultSet rs = statement.getGeneratedKeys()) {
                    assertTrue(rs.next(), "MySQL must return the generated resource ID");
                    resourceId = rs.getInt(1);
                }
            }

            final int testUserId = userId;
            final int testResourceId = resourceId;
            BookingDAO bookings = new BookingDAO();
            LocalDateTime start = LocalDateTime.now().plusDays(3).withSecond(0).withNano(0);
            int firstBooking = bookings.createIfAvailable(resourceId, userId, start, start.plusHours(1));
            assertTrue(firstBooking > 0);

            // Half-open intervals allow a booking to start exactly when the prior one ends.
            int adjacentBooking = bookings.createIfAvailable(
                    resourceId, userId, start.plusHours(1), start.plusHours(2));
            assertTrue(adjacentBooking > 0);

            SQLException overlap = assertThrows(SQLException.class,
                    () -> bookings.createIfAvailable(testResourceId, testUserId,
                            start.plusMinutes(30), start.plusMinutes(45)));
            assertEquals("23P01", overlap.getSQLState());

            // Two simultaneous requests for a capacity-one resource must not both commit.
            LocalDateTime concurrentStart = start.plusHours(4);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch gate = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = executor.submit(() -> attemptAfterGate(
                        bookings, testResourceId, testUserId, concurrentStart, ready, gate));
                Future<Integer> second = executor.submit(() -> attemptAfterGate(
                        bookings, testResourceId, testUserId, concurrentStart, ready, gate));
                assertTrue(ready.await(5, TimeUnit.SECONDS), "Booking workers did not become ready");
                gate.countDown();

                int successes = 0;
                int conflicts = 0;
                for (Future<Integer> attempt : java.util.List.of(first, second)) {
                    try {
                        assertTrue(attempt.get(15, TimeUnit.SECONDS) > 0);
                        successes++;
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                            conflicts++;
                        } else {
                            throw e;
                        }
                    }
                }
                assertEquals(1, successes, "Exactly one concurrent booking should succeed");
                assertEquals(1, conflicts, "The other concurrent booking should receive a capacity conflict");
            } finally {
                executor.shutdownNow();
            }
        } finally {
            try (Connection connection = openAdminConnection()) {
                if (resourceId > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM bookings WHERE resource_id = ?")) {
                        statement.setInt(1, resourceId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM resources WHERE id = ?")) {
                        statement.setInt(1, resourceId);
                        statement.executeUpdate();
                    }
                }
                if (userId > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM users WHERE id = ?")) {
                        statement.setInt(1, userId);
                        statement.executeUpdate();
                    }
                }
            } finally {
                DBConnection.closePool();
            }
        }
    }

    private int attemptAfterGate(BookingDAO bookings, int resourceId, int userId, LocalDateTime start,
                                 CountDownLatch ready, CountDownLatch gate) throws Exception {
        ready.countDown();
        if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Booking test start timed out");
        return bookings.createIfAvailable(resourceId, userId, start, start.plusHours(1));
    }

    private Connection openAdminConnection() throws SQLException {
        String url = System.getenv("CAMPUSOS_DB_URL");
        String username = System.getenv("CAMPUSOS_DB_USERNAME");
        String password = System.getenv("CAMPUSOS_DB_PASSWORD");
        if (url == null || username == null || password == null) {
            throw new SQLException("MySQL integration test database environment is incomplete");
        }
        return DriverManager.getConnection(url, username, password);
    }
}
