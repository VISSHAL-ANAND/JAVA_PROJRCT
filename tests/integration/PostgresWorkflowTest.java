package integration;

import dao.AdminDAO;
import dao.BookingDAO;
import dao.EmergencyDAO;
import dao.NotificationDAO;
import dao.TicketDAO;
import dao.UserDAO;
import model.Issue;
import model.Priority;
import model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import security.PasswordHasher;
import service.AuthService;
import service.EmergencyService;
import service.IssueService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_IT_ENABLED", matches = "true")
class PostgresWorkflowTest {
    @Test
    void postgresSupportsAuthTicketsNotificationsEmergenciesAndBookings() throws Exception {
        UserDAO users = new UserDAO();
        TicketDAO tickets = new TicketDAO();
        NotificationDAO notifications = new NotificationDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String studentEmail = "student-" + suffix + "@example.test";
        String techEmail = "tech-" + suffix + "@example.test";

        int studentId = users.createUser("IT Student " + suffix, studentEmail, PasswordHasher.hash(password),
                "STUDENT", "IT" + suffix, "CSE", null);
        int technicianId = users.createUser("IT Technician " + suffix, techEmail, PasswordHasher.hash(password),
                "TECHNICIAN", null, "CSE", "Electrical");
        int adminId = users.createUser("IT Admin " + suffix, "admin-" + suffix + "@example.test",
                PasswordHasher.hash(password), "ADMIN", null, "CSE", null);

        User authenticatedTechnician = new AuthService(users).authenticate(techEmail, password);
        assertEquals("TECHNICIAN", authenticatedTechnician.getRole());

        Issue criticalIssue = new Issue(0, "Gas leak in lab", "Gas leak reported near the electrical lab",
                "Electrical", "Block A", null, null);
        int criticalTicketId = new IssueService().submit(criticalIssue, studentId);
        assertEquals(Priority.CRITICAL, criticalIssue.getPriority());

        Map<String, Object> firstTicket = tickets.findByReporter(studentId).stream()
                .filter(ticket -> ((Number) ticket.get("id")).intValue() == criticalTicketId)
                .findFirst().orElseThrow();
        assertEquals("ASSIGNED", firstTicket.get("status"));
        assertEquals("CRITICAL", firstTicket.get("priority"));
        assertEquals("OPEN", tickets.findHistory(criticalTicketId).get(0).get("fromStatus"));
        assertEquals("ASSIGNED", tickets.findHistory(criticalTicketId).get(0).get("toStatus"));

        assertThrows(SecurityException.class,
                () -> tickets.updateStatus(criticalTicketId, studentId, "STUDENT", "IN_PROGRESS"));
        tickets.updateStatus(criticalTicketId, technicianId, "TECHNICIAN", "IN_PROGRESS");
        tickets.updateStatus(criticalTicketId, technicianId, "TECHNICIAN", "RESOLVED");
        tickets.updateStatus(criticalTicketId, technicianId, "TECHNICIAN", "CLOSED");
        assertEquals("CLOSED", tickets.findByReporter(studentId).stream()
                .filter(ticket -> ((Number) ticket.get("id")).intValue() == criticalTicketId)
                .findFirst().orElseThrow().get("status"));

        assertTrue(users.setStaffAvailability(technicianId, false));

        // Other integration tests may have left staff fixtures in this disposable database.
        // Snapshot and temporarily disable only staff who were available, so this assertion
        // truly exercises the no-available-technician branch regardless of test order.
        List<Integer> previouslyAvailableStaff = new ArrayList<>();
        try (Connection connection = openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM users WHERE role IN ('MAINTENANCE','TECHNICIAN') AND available=TRUE");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) previouslyAvailableStaff.add(result.getInt("id"));
        }
        for (int availableStaffId : previouslyAvailableStaff) {
            assertTrue(users.setStaffAvailability(availableStaffId, false));
        }

        int unassignedTicketId;
        try {
            Issue unassignedIssue = new Issue(0, "Broken chair", "Chair is not working", "Furniture",
                    "Room 101", null, null);
            unassignedTicketId = new IssueService().submit(unassignedIssue, studentId);
            assertEquals("OPEN", tickets.findByReporter(studentId).stream()
                    .filter(ticket -> ((Number) ticket.get("id")).intValue() == unassignedTicketId)
                    .findFirst().orElseThrow().get("status"));
        } finally {
            for (int availableStaffId : previouslyAvailableStaff) {
                assertTrue(users.setStaffAvailability(availableStaffId, true));
            }
            assertTrue(users.setStaffAvailability(technicianId, true));
        }
        tickets.assignTicketToStaff(unassignedTicketId, technicianId, adminId);
        assertEquals("ASSIGNED", tickets.findByReporter(studentId).stream()
                .filter(ticket -> ((Number) ticket.get("id")).intValue() == unassignedTicketId)
                .findFirst().orElseThrow().get("status"));

        // A staff member must not update a ticket assigned to a different staff account.
        int otherTechnicianId = users.createUser("IT Other Technician " + suffix,
                "other-tech-" + suffix + "@example.test", PasswordHasher.hash(password),
                "TECHNICIAN", null, "CSE", "Plumbing");
        assertTrue(users.setStaffAvailability(technicianId, false));
        Issue otherStaffIssue = new Issue(0, "Leaking pipe", "Water leaking near the lab",
                "Plumbing", "Block B", null, null);
        int otherStaffTicketId = new IssueService().submit(otherStaffIssue, studentId);
        assertTrue(users.setStaffAvailability(technicianId, true));
        assertEquals(otherTechnicianId, tickets.findAssignedTo(otherTechnicianId, false).stream()
                .filter(ticket -> ((Number) ticket.get("id")).intValue() == otherStaffTicketId)
                .map(ticket -> ((Number) ticket.get("assignedTechnicianId")).intValue())
                .findFirst().orElseThrow());
        assertThrows(SecurityException.class,
                () -> tickets.updateStatus(otherStaffTicketId, technicianId, "TECHNICIAN", "IN_PROGRESS"));
        tickets.updateStatus(otherStaffTicketId, otherTechnicianId, "TECHNICIAN", "IN_PROGRESS");

        List<Map<String, Object>> inbox = notifications.findByUser(studentId);
        assertFalse(inbox.isEmpty());
        assertTrue(notifications.markRead(studentId, ((Number) inbox.get(0).get("id")).intValue()));

        String emergencyTitle = "Integration alert";
        String emergencyBody = "Follow the posted safety instructions.";
        int emergencyId = new EmergencyService().createEmergency(adminId, emergencyTitle, emergencyBody);
        assertTrue(emergencyId > 0);
        assertTrue(new EmergencyDAO().findRecent().stream()
                .anyMatch(item -> ((Number) item.get("id")).intValue() == emergencyId));

        // Every other account receives exactly one broadcast notification; the creator is excluded.
        String emergencyNotification = "EMERGENCY: " + emergencyTitle + " - " + emergencyBody;
        for (int recipientId : List.of(studentId, technicianId, otherTechnicianId)) {
            long matchingNotifications = notifications.findByUser(recipientId).stream()
                    .filter(item -> emergencyNotification.equals(item.get("message")))
                    .count();
            assertEquals(1L, matchingNotifications,
                    "Each non-creator account must receive exactly one emergency notification");
        }
        long creatorNotifications = notifications.findByUser(adminId).stream()
                .filter(item -> emergencyNotification.equals(item.get("message")))
                .count();
        assertEquals(0L, creatorNotifications, "The emergency creator must not receive their own broadcast");

        int resourceId;
        try (Connection connection = openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO resources(name,type,quantity,available) VALUES(?,?,?,TRUE)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "IT Room " + suffix);
            statement.setString(2, "ROOM");
            statement.setInt(3, 1);
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                assertTrue(rs.next());
                resourceId = rs.getInt(1);
            }
        }

        BookingDAO bookings = new BookingDAO();
        LocalDateTime start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        int bookingId = bookings.createIfAvailable(resourceId, studentId, start, end);
        assertTrue(bookingId > 0);
        SQLException conflict = assertThrows(SQLException.class,
                () -> bookings.createIfAvailable(resourceId, adminId, start.plusMinutes(30), end.plusMinutes(30)));
        assertEquals("23P01", conflict.getSQLState());
        assertEquals(1, bookings.findByUser(studentId).size());
        assertFalse(bookings.cancelFutureBooking(bookingId, adminId),
                "A different user must not cancel someone else's booking");
        assertEquals(1, bookings.findByUser(studentId).size());
        assertTrue(bookings.cancelFutureBooking(bookingId, studentId));
        assertTrue(bookings.findByUser(studentId).isEmpty());

        // A burst of 25 simultaneous requests must still honor capacity one.
        final int contenderCount = 25;
        CountDownLatch ready = new CountDownLatch(contenderCount);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(contenderCount);
        try {
            List<Future<Integer>> attempts = new ArrayList<>(contenderCount);
            for (int i = 0; i < contenderCount; i++) {
                final int contenderUserId = switch (i % 3) {
                    case 0 -> studentId;
                    case 1 -> adminId;
                    default -> technicianId;
                };
                attempts.add(pool.submit(() -> {
                    ready.countDown();
                    if (!startGate.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Booking test start timed out");
                    }
                    return bookings.createIfAvailable(
                            resourceId, contenderUserId, start.plusHours(3), end.plusHours(3));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Booking workers did not become ready");
            startGate.countDown();
            int successes = 0;
            int conflicts = 0;
            for (Future<Integer> attempt : attempts) {
                try {
                    assertTrue(attempt.get(30, TimeUnit.SECONDS) > 0);
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
            assertEquals(contenderCount - 1, conflicts,
                    "Every other concurrent booking must receive a capacity conflict");
        } finally {
            pool.shutdownNow();
        }

        // Capacity is simultaneous occupancy, not the number of bookings touching the interval.
        int capacityTwoResourceId;
        try (Connection connection = openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO resources(name,type,quantity,available) VALUES(?,?,2,TRUE)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "IT Shared Room " + suffix);
            statement.setString(2, "ROOM");
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                assertTrue(rs.next());
                capacityTwoResourceId = rs.getInt(1);
            }
        }

        LocalDateTime sharedStart = LocalDateTime.now().plusDays(4).withHour(9).withMinute(0).withSecond(0).withNano(0);
        bookings.createIfAvailable(capacityTwoResourceId, studentId, sharedStart, sharedStart.plusHours(1));
        bookings.createIfAvailable(capacityTwoResourceId, adminId, sharedStart.plusHours(1), sharedStart.plusHours(2));
        int spanningBookingId = bookings.createIfAvailable(
                capacityTwoResourceId, technicianId, sharedStart, sharedStart.plusHours(2));
        assertTrue(spanningBookingId > 0,
                "Capacity 2 must allow a booking spanning two sequential bookings when peak occupancy is 1");

        SQLException trueCapacityConflict = assertThrows(SQLException.class,
                () -> bookings.createIfAvailable(capacityTwoResourceId, studentId,
                        sharedStart.plusMinutes(30), sharedStart.plusMinutes(90)));
        assertEquals("23P01", trueCapacityConflict.getSQLState(),
                "A third simultaneous booking must be rejected when capacity is 2");

        Map<String, Integer> reporterSummary = tickets.getReporterTicketSummary(studentId);
        assertEquals(3, reporterSummary.get("total"));
        assertEquals(2, reporterSummary.get("openCount"));
        assertEquals(1, reporterSummary.get("resolvedCount"));

        Map<String, Object> overview = new AdminDAO().getOverview();
        assertTrue(((Number) overview.get("totalUsers")).intValue() >= 3);
        assertTrue(((Number) overview.get("totalTickets")).intValue() >= 2);
    }
    @Test
    void unreadNotificationLookupIsBoundedAndNewestFirst() throws Exception {
        UserDAO users = new UserDAO();
        NotificationDAO notifications = new NotificationDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        int userId = users.createUser("Unread Test " + suffix, "unread-" + suffix + "@example.test",
                PasswordHasher.hash("IntegrationPass!2026"), "STUDENT", "UNREAD" + suffix, "CSE", null);

        try (Connection connection = openFixtureConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO notifications(user_id, message, is_read, created_at) " +
                             "VALUES(?, ?, FALSE, CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))")) {
            for (int i = 0; i < 105; i++) {
                statement.setInt(1, userId);
                statement.setString(2, "unread-" + i);
                statement.setInt(3, i);
                statement.addBatch();
            }
            int[] inserted = statement.executeBatch();
            assertEquals(105, inserted.length);
        }

        List<String> unread = notifications.findUnreadMessages(userId);
        assertEquals(100, unread.size(), "Unread lookup must not load an unbounded notification history");
        assertEquals("unread-104", unread.get(0), "Newest unread message must be returned first");
        assertEquals("unread-5", unread.get(99), "The result must contain only the 100 newest unread messages");
    }

    private Connection openFixtureConnection() throws SQLException {
        String adminUrl = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
        if (adminUrl == null || adminUrl.isBlank()) {
            return database.DBConnection.getConnection();
        }
        String adminUsername = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
        String adminPassword = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null) {
            throw new SQLException("Test fixture admin connection settings are incomplete");
        }
        return DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
    }

}
