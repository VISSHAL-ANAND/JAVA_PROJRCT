package integration;

import api.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dao.IssueDAO;
import dao.TicketDAO;
import dao.UserDAO;
import model.Issue;
import model.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import security.PasswordHasher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_IT_ENABLED", matches = "true")
class AdminTicketCsvExportTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void administratorCanExportTicketReportButStudentCannot() throws Exception {
        UserDAO users = new UserDAO();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String password = "IntegrationPass!2026";
        String adminEmail = "csv-admin-" + suffix + "@example.test";
        String studentEmail = "csv-student-" + suffix + "@example.test";
        int adminId = users.createUser("CSV Admin " + suffix, adminEmail,
                PasswordHasher.hash(password), "ADMIN", null, "CSE", null);
        int studentId = users.createUser("CSV Student " + suffix, studentEmail,
                PasswordHasher.hash(password), "STUDENT", "CSV" + suffix, "CSE", null);
        String title = "CSV export fixture " + suffix;
        int issueId = new IssueDAO().createIssue(new Issue(0, title, "CSV report integration fixture",
                "Facilities", "CSV Block", null, Priority.MEDIUM));
        new TicketDAO().createTicket(issueId, studentId);

        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            String base = "http://127.0.0.1:" + server.getPort();
            String adminToken = login(client, base, adminEmail, password);
            HttpResponse<String> report = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/reports/tickets.csv"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + adminToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, report.statusCode(), report.body());
            assertTrue(report.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
            assertTrue(report.headers().firstValue("Content-Disposition").orElse("")
                    .contains("campusos-tickets.csv"));
            assertTrue(report.body().startsWith("\"ticket_id\",\"title\""));
            assertTrue(report.body().contains(title));
            assertTrue(report.body().contains(studentEmail));

            String studentToken = login(client, base, studentEmail, password);
            HttpResponse<String> denied = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/reports/tickets.csv"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + studentToken)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, denied.statusCode(), denied.body());
        } finally {
            server.stop();
        }
    }

    private static String login(HttpClient client, String base, String email, String password) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                Map.of("email", email, "password", password))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        String token = body.path("token").asText();
        assertFalse(token.isBlank(), "Login must return a bearer token");
        return token;
    }
}
