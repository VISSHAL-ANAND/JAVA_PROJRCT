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
        int otherStudentId = users.createUser("HTTP Other Student " + suffix,
                "http-other-" + suffix + "@example.test", PasswordHasher.hash(password),
                "STUDENT", "OTHER" + suffix, "CSE", null);

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

            HttpResponse<String> otherTickets = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/reporter/" + otherStudentId))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, otherTickets.statusCode(), otherTickets.body());
            assertTrue(otherTickets.body().contains("only view your own tickets"));
        } finally {
            server.stop();
        }
    }
}
