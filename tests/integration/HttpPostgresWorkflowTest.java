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
        users.createUser("HTTP Assigned Technician " + suffix, technicianEmail,
                PasswordHasher.hash(password), "TECHNICIAN", null, "CSE", "Facilities");
        String otherTechnicianEmail = "http-other-tech-" + suffix + "@example.test";
        users.createUser("HTTP Other Technician " + suffix, otherTechnicianEmail,
                PasswordHasher.hash(password), "TECHNICIAN", null, "CSE", "Plumbing");

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
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets/" + ticketId + "/status"))
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
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets/" + ticketId + "/status"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + otherTechnicianToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"status\":\"RESOLVED\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, deniedOtherTechnician.statusCode(), deniedOtherTechnician.body());
            assertTrue(deniedOtherTechnician.body().contains("not assigned to your account"));

            HttpResponse<String> deniedStudentUpdate = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets/" + ticketId + "/status"))
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
