package integration;

import dao.BookingDAO;
import dao.NotificationDAO;
import dao.TicketDAO;
import database.DBConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import model.Issue;
import model.Priority;
import service.IssueService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
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

    @Test
    void mysqlIssueTicketAssignmentNotificationsAndLifecycleWorkEndToEnd() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String title = "MySQL IT Network outage " + suffix;
        int studentId = -1;
        int technicianId = -1;
        try {
            studentId = insertTestUser("MySQL IT Student " + suffix,
                    "mysql-student-" + suffix + "@example.test", "STUDENT", null);
            technicianId = insertTestUser("MySQL IT Technician " + suffix,
                    "mysql-tech-" + suffix + "@example.test", "TECHNICIAN", "Network");

            Issue issue = new Issue(0, title, "Network down in teaching lab", "Network", "Lab 4", null, null);
            int ticketId = new IssueService().submit(issue, studentId);
            assertTrue(ticketId > 0);
            assertEquals(Priority.HIGH, issue.getPriority());

            TicketDAO tickets = new TicketDAO();
            Map<String, Object> reporterTicket = tickets.findByReporter(studentId).stream()
                    .filter(item -> ((Number) item.get("id")).intValue() == ticketId)
                    .findFirst().orElseThrow();
            assertEquals("ASSIGNED", reporterTicket.get("status"));
            assertEquals("HIGH", reporterTicket.get("priority"));

            Map<String, Object> staffTicket = tickets.findAssignedTo(technicianId, false).stream()
                    .filter(item -> ((Number) item.get("id")).intValue() == ticketId)
                    .findFirst().orElseThrow();
            assertEquals(technicianId, ((Number) staffTicket.get("assignedTechnicianId")).intValue());

            Map<String, Integer> summary = tickets.getReporterTicketSummary(studentId);
            assertEquals(1, summary.get("total"));
            assertEquals(1, summary.get("openCount"));
            assertEquals(0, summary.get("resolvedCount"));

            tickets.updateStatus(ticketId, technicianId, "TECHNICIAN", "IN_PROGRESS");
            tickets.updateStatus(ticketId, technicianId, "TECHNICIAN", "RESOLVED");
            tickets.updateStatus(ticketId, technicianId, "TECHNICIAN", "CLOSED");
            assertEquals("CLOSED", tickets.findByReporter(studentId).stream()
                    .filter(item -> ((Number) item.get("id")).intValue() == ticketId)
                    .findFirst().orElseThrow().get("status"));

            summary = tickets.getReporterTicketSummary(studentId);
            assertEquals(1, summary.get("total"));
            assertEquals(0, summary.get("openCount"));
            assertEquals(1, summary.get("resolvedCount"));
            assertEquals("CLOSED", tickets.findHistory(ticketId).get(3).get("toStatus"));

            NotificationDAO notifications = new NotificationDAO();
            assertFalse(notifications.findByUser(studentId).isEmpty(),
                    "Reporter should receive the assignment notification");
            assertFalse(notifications.findByUser(technicianId).isEmpty(),
                    "Assigned technician should receive the assignment notification");
        } finally {
            try (Connection connection = openAdminConnection()) {
                if (studentId > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM notifications WHERE user_id = ?")) {
                        statement.setInt(1, studentId);
                        statement.executeUpdate();
                    }
                }
                if (technicianId > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM notifications WHERE user_id = ?")) {
                        statement.setInt(1, technicianId);
                        statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM ticket_history WHERE ticket_id IN (SELECT id FROM tickets WHERE reported_by = ?)")) {
                    statement.setInt(1, studentId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM tickets WHERE reported_by = ? OR assigned_technician_id = ?")) {
                    statement.setInt(1, studentId);
                    statement.setInt(2, technicianId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM issues WHERE title = ?")) {
                    statement.setString(1, title);
                    statement.executeUpdate();
                }
                if (studentId > 0) {
                    try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                        statement.setInt(1, studentId);
                        statement.executeUpdate();
                    }
                }
                if (technicianId > 0) {
                    try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                        statement.setInt(1, technicianId);
                        statement.executeUpdate();
                    }
                }
            } finally {
                DBConnection.closePool();
            }
        }
    }

    private int insertTestUser(String name, String email, String role, String specialization) throws SQLException {
        try (Connection connection = openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users(name,email,password,role,specialization) VALUES(?,?,?, ?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, "test-only-not-a-real-password-hash");
            statement.setString(4, role);
            statement.setString(5, specialization);
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Unable to create MySQL integration fixture user");
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
