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

        users.setStaffAvailability(technicianId, false);
        Issue unassignedIssue = new Issue(0, "Broken chair", "Chair is not working", "Furniture",
                "Room 101", null, null);
        int unassignedTicketId = new IssueService().submit(unassignedIssue, studentId);
        assertEquals("OPEN", tickets.findByReporter(studentId).stream()
                .filter(ticket -> ((Number) ticket.get("id")).intValue() == unassignedTicketId)
                .findFirst().orElseThrow().get("status"));
        assertTrue(users.setStaffAvailability(technicianId, true));
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

        int emergencyId = new EmergencyService().createEmergency(adminId, "Integration alert", "Follow the posted safety instructions.");
        assertTrue(emergencyId > 0);
        assertTrue(new EmergencyDAO().findRecent().stream()
                .anyMatch(item -> ((Number) item.get("id")).intValue() == emergencyId));

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

        // Two simultaneous requests for a capacity-one resource must not both succeed.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstAttempt = pool.submit(() -> {
                ready.countDown();
                if (!startGate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Booking test start timed out");
                return bookings.createIfAvailable(resourceId, studentId, start.plusHours(3), end.plusHours(3));
            });
            Future<Integer> secondAttempt = pool.submit(() -> {
                ready.countDown();
                if (!startGate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Booking test start timed out");
                return bookings.createIfAvailable(resourceId, adminId, start.plusHours(3), end.plusHours(3));
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Booking workers did not become ready");
            startGate.countDown();
            int successes = 0;
            int conflicts = 0;
            for (Future<Integer> attempt : List.of(firstAttempt, secondAttempt)) {
                try {
                    assertTrue(attempt.get(10, TimeUnit.SECONDS) > 0);
                    successes++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                        conflicts++;
                    } else {
                        throw e;
                    }
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
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

        Map<String, Object> overview = new AdminDAO().getOverview();
        assertTrue(((Number) overview.get("totalUsers")).intValue() >= 3);
        assertTrue(((Number) overview.get("totalTickets")).intValue() >= 2);
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
