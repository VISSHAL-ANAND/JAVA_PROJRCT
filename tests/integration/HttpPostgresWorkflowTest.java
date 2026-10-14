package integration;

import api.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dao.UserDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import security.PasswordHasher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_IT_ENABLED", matches = "true")
class HttpPostgresWorkflowTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void authenticatedHttpIssueWorkflowPersistsAndEnforcesReporterOwnership() throws Exception {
        UserDAO users = new UserDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String email = "http-student-" + suffix + "@example.test";
        int studentId = users.createUser("HTTP Student " + suffix, email,
                PasswordHasher.hash(password), "STUDENT", "HTTP" + suffix, "CSE", null);
        String otherEmail = "http-other-" + suffix + "@example.test";
        int otherStudentId = users.createUser("HTTP Other Student " + suffix,
                otherEmail, PasswordHasher.hash(password), "STUDENT", "OTHER" + suffix, "CSE", null);
        String technicianEmail = "http-tech-" + suffix + "@example.test";
        int technicianId = users.createUser("HTTP Assigned Technician " + suffix, technicianEmail,
                PasswordHasher.hash(password), "TECHNICIAN", null, "CSE", "Facilities");
        String otherTechnicianEmail = "http-other-tech-" + suffix + "@example.test";
        users.createUser("HTTP Other Technician " + suffix, otherTechnicianEmail,
                PasswordHasher.hash(password), "TECHNICIAN", null, "CSE", "Plumbing");
        String fixtureAdminEmail = "http-assignment-admin-" + suffix + "@example.test";
        int fixtureAdminId = users.createUser("HTTP Assignment Admin " + suffix, fixtureAdminEmail,
                PasswordHasher.hash(password), "ADMIN", null, "CSE", null);
        int statusIssueId = new dao.IssueDAO().createIssue(
                new model.Issue(0, "HTTP status fixture " + suffix, "Fixture for staff status authorization",
                        "Facilities", "Status Block", null, model.Priority.MEDIUM));
        int statusTicketId = new dao.TicketDAO().createTicket(statusIssueId, studentId);
        new dao.TicketDAO().assignTicketToStaff(statusTicketId, technicianId, fixtureAdminId);

        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            String base = "http://127.0.0.1:" + server.getPort();

            HttpResponse<String> login = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("email", email, "password", password))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode(), login.body());
            String token = JSON.readTree(login.body()).path("token").asText();
            assertFalse(token.isBlank(), "Successful login must issue an access token");

            String issueTitle = "HTTP integration issue " + suffix;
            HttpResponse<String> createIssue = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/issues"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "title", issueTitle,
                                            "description", "Created through the authenticated HTTP API",
                                            "category", "Facilities",
                                            "location", "Integration Block"))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createIssue.statusCode(), createIssue.body());
            JsonNode created = JSON.readTree(createIssue.body());
            int ticketId = created.path("ticketId").asInt();
            assertTrue(ticketId > 0, "API must return the persisted ticket ID");

            HttpResponse<String> ownTickets = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/reporter/" + studentId))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownTickets.statusCode(), ownTickets.body());
            JsonNode ticketList = JSON.readTree(ownTickets.body()).path("tickets");
            assertTrue(ticketList.isArray());
            boolean foundCreatedTicket = false;
            for (JsonNode ticket : ticketList) {
                if (ticket.path("id").asInt() == ticketId
                        && issueTitle.equals(ticket.path("title").asText())) {
                    foundCreatedTicket = true;
                    break;
                }
            }
            assertTrue(foundCreatedTicket,
                    "The HTTP-created issue must be retrievable from PostgreSQL through the reporter endpoint");

            HttpResponse<String> ownHistory = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/" + ticketId + "/history"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownHistory.statusCode(), ownHistory.body());
            assertTrue(JSON.readTree(ownHistory.body()).path("history").isArray());

            HttpResponse<String> otherLogin = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("email", otherEmail, "password", password))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, otherLogin.statusCode(), otherLogin.body());
            String otherToken = JSON.readTree(otherLogin.body()).path("token").asText();
            assertFalse(otherToken.isBlank());

            HttpResponse<String> otherHistory = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/" + ticketId + "/history"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + otherToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, otherHistory.statusCode(), otherHistory.body());
            assertTrue(otherHistory.body().contains("Ticket not found"));

            HttpResponse<String> otherTickets = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/reporter/" + otherStudentId))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, otherTickets.statusCode(), otherTickets.body());
            assertTrue(otherTickets.body().contains("only view your own tickets"));

            HttpResponse<String> technicianLogin = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("email", technicianEmail, "password", password))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, technicianLogin.statusCode(), technicianLogin.body());
            String technicianToken = JSON.readTree(technicianLogin.body()).path("token").asText();
            assertFalse(technicianToken.isBlank());

            HttpResponse<String> assignedUpdate = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets/" + statusTicketId + "/status"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + technicianToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"status\":\"IN_PROGRESS\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, assignedUpdate.statusCode(), assignedUpdate.body());

            HttpResponse<String> otherTechnicianLogin = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("email", otherTechnicianEmail, "password", password))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, otherTechnicianLogin.statusCode(), otherTechnicianLogin.body());
            String otherTechnicianToken = JSON.readTree(otherTechnicianLogin.body()).path("token").asText();
            assertFalse(otherTechnicianToken.isBlank());

            HttpResponse<String> deniedOtherTechnician = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets/" + statusTicketId + "/status"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + otherTechnicianToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"status\":\"RESOLVED\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, deniedOtherTechnician.statusCode(), deniedOtherTechnician.body());
            assertTrue(deniedOtherTechnician.body().contains("not assigned to your account"));

            HttpResponse<String> deniedStudentUpdate = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets/" + statusTicketId + "/status"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"status\":\"RESOLVED\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, deniedStudentUpdate.statusCode(), deniedStudentUpdate.body());
            assertTrue(deniedStudentUpdate.body().contains("Staff access required"));
        } finally {
            server.stop();
        }
    }


    @Test
    void httpBookingLifecycleEnforcesOwnerOnlyCancellation() throws Exception {
        UserDAO users = new UserDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String ownerEmail = "booking-owner-" + suffix + "@example.test";
        int ownerId = users.createUser("Booking Owner " + suffix, ownerEmail,
                PasswordHasher.hash(password), "STUDENT", "BOOK" + suffix, "CSE", null);
        String otherEmail = "booking-other-" + suffix + "@example.test";
        users.createUser("Booking Other " + suffix, otherEmail,
                PasswordHasher.hash(password), "STUDENT", "OTHERBOOK" + suffix, "CSE", null);

        int resourceId;
        try (java.sql.Connection connection = openAdminConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO public.resources(name,type,quantity,available) VALUES(?,?,1,TRUE)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "HTTP Booking Resource " + suffix);
            statement.setString(2, "ROOM");
            statement.executeUpdate();
            try (java.sql.ResultSet result = statement.getGeneratedKeys()) {
                assertTrue(result.next());
                resourceId = result.getInt(1);
            }
        }

        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            String base = "http://127.0.0.1:" + server.getPort();
            String ownerToken = loginToken(client, base, ownerEmail, password);
            String otherToken = loginToken(client, base, otherEmail, password);
            java.time.LocalDateTime start = java.time.LocalDateTime.now()
                    .plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
            java.time.LocalDateTime end = start.plusHours(1);

            HttpResponse<String> wrongMethod = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .PUT(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, wrongMethod.statusCode(),
                    "The booking collection must report unsupported methods as HTTP 405");

            HttpResponse<String> malformedResourceId = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "resourceId", 1.9d,
                                            "startTime", start.toString(),
                                            "endTime", end.toString()))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, malformedResourceId.statusCode(),
                    "A fractional resource ID must be rejected instead of truncated to another resource");

            HttpResponse<String> malformedTime = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "resourceId", resourceId,
                                            "startTime", "not-a-timestamp",
                                            "endTime", end.toString()))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, malformedTime.statusCode(),
                    "Malformed booking timestamps must produce a client error response");

            HttpResponse<String> reversedInterval = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "resourceId", resourceId,
                                            "startTime", end.toString(),
                                            "endTime", start.toString()))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, reversedInterval.statusCode(),
                    "A booking whose end is before its start must be rejected");

            HttpResponse<String> beforeValidBooking = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings/mine"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, beforeValidBooking.statusCode(), beforeValidBooking.body());
            assertEquals(0, JSON.readTree(beforeValidBooking.body()).path("bookings").size(),
                    "Invalid booking requests must not create database rows");

            HttpResponse<String> create = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "resourceId", resourceId,
                                            "startTime", start.toString(),
                                            "endTime", end.toString()))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, create.statusCode(), create.body());
            int bookingId = JSON.readTree(create.body()).path("bookingId").asInt();
            assertTrue(bookingId > 0);

            HttpResponse<String> ownerBookings = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings/mine"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerBookings.statusCode(), ownerBookings.body());
            assertTrue(containsId(JSON.readTree(ownerBookings.body()).path("bookings"), bookingId));

            HttpResponse<String> deniedCancel = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings/" + bookingId + "/cancel"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + otherToken)
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(409, deniedCancel.statusCode(), deniedCancel.body());

            HttpResponse<String> ownerAfterDeniedCancel = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings/mine"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerAfterDeniedCancel.statusCode(), ownerAfterDeniedCancel.body());
            assertTrue(containsId(JSON.readTree(ownerAfterDeniedCancel.body()).path("bookings"), bookingId),
                    "A denied cross-user cancellation must leave the owner's booking intact");

            HttpResponse<String> ownerCancel = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings/" + bookingId + "/cancel"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerCancel.statusCode(), ownerCancel.body());

            HttpResponse<String> ownerAfterCancel = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/bookings/mine"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + ownerToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerAfterCancel.statusCode(), ownerAfterCancel.body());
            assertFalse(containsId(JSON.readTree(ownerAfterCancel.body()).path("bookings"), bookingId));
        } finally {
            server.stop();
        }
    }

    private String loginToken(HttpClient client, String base, String email, String password) throws Exception {
        HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                java.util.Map.of("email", email, "password", password))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        String token = JSON.readTree(login.body()).path("token").asText();
        assertFalse(token.isBlank(), "Successful login must issue an access token");
        return token;
    }

    private boolean containsId(JsonNode array, int expectedId) {
        if (!array.isArray()) return false;
        for (JsonNode item : array) {
            if (item.path("id").asInt() == expectedId) return true;
        }
        return false;
    }



    @Test
    void httpEmergencyBroadcastPersistsAndNotifiesRecipients() throws Exception {
        UserDAO users = new UserDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String studentEmail = "emergency-student-" + suffix + "@example.test";
        int studentId = users.createUser("Emergency Student " + suffix, studentEmail,
                PasswordHasher.hash(password), "STUDENT", "EMERG" + suffix, "CSE", null);
        String otherStudentEmail = "emergency-other-" + suffix + "@example.test";
        users.createUser("Emergency Other Student " + suffix, otherStudentEmail,
                PasswordHasher.hash(password), "STUDENT", "OTHEREM" + suffix, "CSE", null);
        String adminEmail = "emergency-admin-" + suffix + "@example.test";
        users.createUser("Emergency Admin " + suffix, adminEmail,
                PasswordHasher.hash(password), "ADMIN", null, "CSE", null);

        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            String base = "http://127.0.0.1:" + server.getPort();
            String studentToken = loginToken(client, base, studentEmail, password);
            String adminToken = loginToken(client, base, adminEmail, password);
            String title = "HTTP emergency " + suffix;
            String message = "Follow the posted safety instructions for " + suffix;
            String expectedNotification = "EMERGENCY: " + title + " - " + message;

            HttpResponse<String> create = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/emergencies"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("title", title, "message", message))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, create.statusCode(), create.body());
            int emergencyId = JSON.readTree(create.body()).path("id").asInt();
            assertTrue(emergencyId > 0);

            HttpResponse<String> feed = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/emergencies"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, feed.statusCode(), feed.body());
            boolean foundEmergency = false;
            for (JsonNode emergency : JSON.readTree(feed.body()).path("emergencies")) {
                if (emergency.path("id").asInt() == emergencyId
                        && title.equals(emergency.path("title").asText())
                        && message.equals(emergency.path("message").asText())) {
                    foundEmergency = true;
                    break;
                }
            }
            assertTrue(foundEmergency, "The authenticated emergency feed must expose the persisted broadcast");

            HttpResponse<String> studentInbox = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/notifications/me"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, studentInbox.statusCode(), studentInbox.body());
            long studentMatches = 0;
            int studentNotificationId = 0;
            for (JsonNode notification : JSON.readTree(studentInbox.body()).path("notifications")) {
                if (expectedNotification.equals(notification.path("message").asText())) {
                    studentMatches++;
                    studentNotificationId = notification.path("id").asInt();
                }
            }
            assertEquals(1L, studentMatches, "Each non-creator recipient must receive exactly one matching notification");
            assertTrue(studentNotificationId > 0);

            String otherStudentToken = loginToken(client, base, otherStudentEmail, password);
            HttpResponse<String> deniedRead = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/notifications/" + studentNotificationId + "/read"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + otherStudentToken)
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, deniedRead.statusCode(), deniedRead.body());

            HttpResponse<String> ownerRead = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/notifications/" + studentNotificationId + "/read"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ownerRead.statusCode(), ownerRead.body());

            HttpResponse<String> adminInbox = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/notifications/me"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, adminInbox.statusCode(), adminInbox.body());
            long adminMatches = 0;
            for (JsonNode notification : JSON.readTree(adminInbox.body()).path("notifications")) {
                if (expectedNotification.equals(notification.path("message").asText())) adminMatches++;
            }
            assertEquals(0L, adminMatches, "The emergency creator must not receive their own broadcast notification");

            HttpResponse<String> deniedStudentBroadcast = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/emergencies"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("title", "Unauthorized " + suffix, "message", "Not allowed"))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, deniedStudentBroadcast.statusCode(), deniedStudentBroadcast.body());
        } finally {
            server.stop();
        }
    }



    @Test
    void adminCanAssignOpenTicketOverHttpAndStudentsCannot() throws Exception {
        UserDAO users = new UserDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String studentEmail = "assign-student-" + suffix + "@example.test";
        int studentId = users.createUser("Assignment Student " + suffix, studentEmail,
                PasswordHasher.hash(password), "STUDENT", "ASGN" + suffix, "CSE", null);
        String adminEmail = "assign-admin-" + suffix + "@example.test";
        users.createUser("Assignment Admin " + suffix, adminEmail,
                PasswordHasher.hash(password), "ADMIN", null, "CSE", null);
        String technicianEmail = "assign-tech-" + suffix + "@example.test";
        int technicianId = users.createUser("Assignment Technician " + suffix, technicianEmail,
                PasswordHasher.hash(password), "TECHNICIAN", null, "CSE", "Facilities");

        String issueTitle = "HTTP admin assignment " + suffix;
        int issueId = new dao.IssueDAO().createIssue(
                new model.Issue(0, issueTitle, "Fixture issue for admin assignment",
                        "Facilities", "Assignment Block", null, model.Priority.MEDIUM));
        int ticketId = new dao.TicketDAO().createTicket(issueId, studentId);

        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            String base = "http://127.0.0.1:" + server.getPort();
            String adminToken = loginToken(client, base, adminEmail, password);
            String technicianToken = loginToken(client, base, technicianEmail, password);
            String studentToken = loginToken(client, base, studentEmail, password);

            HttpResponse<String> malformedAssignment = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/tickets/" + ticketId + "/assign"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("staffId", 1.9d))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, malformedAssignment.statusCode(),
                    "A fractional staff ID must be rejected instead of truncated to another account ID");

            HttpResponse<String> assignment = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/tickets/" + ticketId + "/assign"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("staffId", technicianId))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, assignment.statusCode(), assignment.body());

            HttpResponse<String> studentAssignment = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/tickets/" + ticketId + "/assign"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("staffId", technicianId))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, studentAssignment.statusCode(), studentAssignment.body());

            HttpResponse<String> staffQueue = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + technicianToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, staffQueue.statusCode(), staffQueue.body());
            boolean foundAssignedTicket = false;
            for (JsonNode ticket : JSON.readTree(staffQueue.body()).path("tickets")) {
                if (ticket.path("id").asInt() == ticketId
                        && "ASSIGNED".equals(ticket.path("status").asText())) {
                    foundAssignedTicket = true;
                    break;
                }
            }
            assertTrue(foundAssignedTicket, "The assigned technician must see the ticket in their staff queue");

            HttpResponse<String> history = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/" + ticketId + "/history"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, history.statusCode(), history.body());
            boolean foundAssignmentHistory = false;
            for (JsonNode entry : JSON.readTree(history.body()).path("history")) {
                if ("OPEN".equals(entry.path("fromStatus").asText())
                        && "ASSIGNED".equals(entry.path("toStatus").asText())) {
                    foundAssignmentHistory = true;
                    break;
                }
            }
            assertTrue(foundAssignmentHistory, "The assignment must be recorded in ticket history");
        } finally {
            server.stop();
        }
    }



    @Test
    void adminUserAndStaffAvailabilityRoutesEnforceRolesOverHttp() throws Exception {
        UserDAO users = new UserDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String adminEmail = "manage-admin-" + suffix + "@example.test";
        users.createUser("Management Admin " + suffix, adminEmail,
                PasswordHasher.hash(password), "ADMIN", null, "CSE", null);
        String studentEmail = "manage-student-" + suffix + "@example.test";
        users.createUser("Management Student " + suffix, studentEmail,
                PasswordHasher.hash(password), "STUDENT", "MGMT" + suffix, "CSE", null);

        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            String base = "http://127.0.0.1:" + server.getPort();
            String adminToken = loginToken(client, base, adminEmail, password);
            String studentToken = loginToken(client, base, studentEmail, password);
            String managedEmail = "managed-tech-" + suffix + "@example.test";
            String managedName = "Managed Technician " + suffix;

            HttpResponse<String> createUser = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "name", managedName,
                                            "email", managedEmail,
                                            "password", password,
                                            "role", "TECHNICIAN",
                                            "department", "CSE",
                                            "specialization", "Facilities"))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createUser.statusCode(), createUser.body());
            int managedUserId = JSON.readTree(createUser.body()).path("userId").asInt();
            assertTrue(managedUserId > 0);

            HttpResponse<String> adminUsers = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, adminUsers.statusCode(), adminUsers.body());
            boolean foundManagedUser = false;
            for (JsonNode user : JSON.readTree(adminUsers.body()).path("users")) {
                if (user.path("id").asInt() == managedUserId
                        && managedEmail.equals(user.path("email").asText())
                        && "TECHNICIAN".equals(user.path("role").asText())) {
                    foundManagedUser = true;
                    assertFalse(user.has("passwordHash"));
                    assertFalse(user.has("password_hash"));
                    break;
                }
            }
            assertTrue(foundManagedUser, "Admin user list must expose the created staff member without password hashes");

            HttpResponse<String> unavailable = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users/" + managedUserId + "/availability"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    JSON.writeValueAsString(java.util.Map.of("available", false))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, unavailable.statusCode(), unavailable.body());

            HttpResponse<String> studentAvailability = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users/" + managedUserId + "/availability"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    JSON.writeValueAsString(java.util.Map.of("available", true))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, studentAvailability.statusCode(), studentAvailability.body());

            HttpResponse<String> staffList = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/staff"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, staffList.statusCode(), staffList.body());
            boolean foundManagedStaff = false;
            for (JsonNode staff : JSON.readTree(staffList.body()).path("staff")) {
                if (staff.path("id").asInt() == managedUserId) {
                    foundManagedStaff = true;
                    break;
                }
            }
            assertFalse(foundManagedStaff,
                    "The student must not re-enable an unavailable staff member");

            HttpResponse<String> restoreAvailability = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users/" + managedUserId + "/availability"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    JSON.writeValueAsString(java.util.Map.of("available", true))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, restoreAvailability.statusCode(), restoreAvailability.body());

            HttpResponse<String> staffAfterRestore = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/staff"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, staffAfterRestore.statusCode(), staffAfterRestore.body());
            boolean foundRestoredStaff = false;
            for (JsonNode staff : JSON.readTree(staffAfterRestore.body()).path("staff")) {
                if (staff.path("id").asInt() == managedUserId) {
                    foundRestoredStaff = true;
                    break;
                }
            }
            assertTrue(foundRestoredStaff, "Admin must be able to restore staff availability");

            HttpResponse<String> studentCreate = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "name", "Unauthorized " + suffix,
                                            "email", "unauthorized-" + suffix + "@example.test",
                                            "password", password,
                                            "role", "TECHNICIAN",
                                            "department", "CSE",
                                            "specialization", "Facilities"))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, studentCreate.statusCode(), studentCreate.body());

            HttpResponse<String> invalidRole = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of(
                                            "name", "Invalid Role " + suffix,
                                            "email", "invalid-role-" + suffix + "@example.test",
                                            "password", password,
                                            "role", "SUPERUSER",
                                            "department", "CSE"))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalidRole.statusCode(), invalidRole.body());
        } finally {
            server.stop();
        }
    }


    @Test
    void httpRejectsTokenAfterDatabaseRoleChanges() throws Exception {
        UserDAO users = new UserDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String email = "role-change-" + suffix + "@example.test";
        int studentId = users.createUser("Role Change Student " + suffix, email,
                PasswordHasher.hash(password), "STUDENT", "ROLE" + suffix, "CSE", null);

        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            String base = "http://127.0.0.1:" + server.getPort();

            HttpResponse<String> login = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                    java.util.Map.of("email", email, "password", password))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode(), login.body());
            String token = JSON.readTree(login.body()).path("token").asText();
            assertFalse(token.isBlank());

            // Change only this test-created account using the CI fixture-admin connection.
            try (java.sql.Connection connection = openAdminConnection();
                 java.sql.PreparedStatement statement = connection.prepareStatement(
                         "UPDATE public.users SET role = ? WHERE id = ?")) {
                statement.setString(1, "FACULTY");
                statement.setInt(2, studentId);
                assertEquals(1, statement.executeUpdate());
            }

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/reporter/" + studentId))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode(), response.body());
            assertTrue(response.body().contains("permissions changed"));
        } finally {
            server.stop();
        }
    }

    private java.sql.Connection openAdminConnection() throws java.sql.SQLException {
        String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
        String username = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
        String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
        if (url == null || url.isBlank() || username == null || username.isBlank() || password == null) {
            throw new java.sql.SQLException("PostgreSQL integration fixture-admin settings are required");
        }
        return java.sql.DriverManager.getConnection(url, username, password);
    }

}
