package integration;

import api.ApiServer;
import dao.UserDAO;
import database.DBConnection;
import model.Admin;
import model.Issue;
import model.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import service.IssueService;
import security.AccessTokenService;
import security.PasswordHasher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_STAFF_TICKET_PAGINATION_TEST_ENABLED", matches = "true")
class StaffTicketPaginationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int FIXTURE_TICKETS = 125;

    @Test
    void staffQueuePagesBeyondFormerHundredTicketLimitWithoutDuplicates() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emailPrefix = "staff-page-" + suffix + "-";
        String titlePrefix = "Staff Page " + suffix + " - ";
        String hash = PasswordHasher.hash("StaffPagination!Test-2026");
        UserDAO users = new UserDAO();
        IssueService issues = new IssueService();
        Set<Integer> fixtureUsers = new HashSet<>();
        int adminId = 0;
        int reporterId = 0;
        ApiServer server = null;
        try {
            adminId = users.createUser("Pagination Admin " + suffix, emailPrefix + "admin@example.test",
                    hash, "ADMIN", null, "CSE", null);
            fixtureUsers.add(adminId);
            reporterId = users.createUser("Pagination Reporter " + suffix, emailPrefix + "reporter@example.test",
                    hash, "STUDENT", "SPAGE" + suffix, "CSE", null);
            fixtureUsers.add(reporterId);
            int technicianId = users.createUser("Pagination Technician " + suffix,
                    emailPrefix + "tech@example.test", hash, "TECHNICIAN", null, "CSE", "Electrical");
            fixtureUsers.add(technicianId);

            for (int i = 0; i < FIXTURE_TICKETS; i++) {
                issues.submit(new Issue(0, titlePrefix + i, "Ticket pagination test fixture",
                        "Electrical", "Building A", null, Priority.MEDIUM), reporterId);
            }

            String token = new AccessTokenService().issue(
                    new Admin(adminId, "Pagination Admin " + suffix, emailPrefix + "admin@example.test", ""));
            server = new ApiServer(0);
            server.start();
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
            String base = "http://127.0.0.1:" + server.getPort() + "/api/staff/tickets";
            Set<Integer> seenIds = new HashSet<>();
            int foundFixtures = 0;
            int totalTickets = -1;
            int totalPages = -1;

            for (int page = 1; page <= Math.max(1, totalPages); page++) {
                // totalPages is learned from the first response below.
                break;
            }
            HttpResponse<String> first = client.send(
                    HttpRequest.newBuilder(URI.create(base + "?page=1&pageSize=50"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, first.statusCode(), first.body());
            JsonNode firstRoot = JSON.readTree(first.body());
            assertTrue(firstRoot.path("success").asBoolean());
            totalTickets = firstRoot.path("totalTickets").asInt();
            totalPages = firstRoot.path("totalPages").asInt();
            assertTrue(totalTickets >= FIXTURE_TICKETS, "All fixture tickets must be included in the admin queue");
            assertTrue(totalPages >= 3, "Ticket fixtures must span at least three pages");

            for (int page = 1; page <= totalPages; page++) {
                HttpResponse<String> response = page == 1 ? first : client.send(
                        HttpRequest.newBuilder(URI.create(base + "?page=" + page + "&pageSize=50"))
                                .header("Authorization", "Bearer " + token).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                JsonNode root = JSON.readTree(response.body());
                assertEquals(page, root.path("page").asInt());
                assertEquals(50, root.path("pageSize").asInt());
                assertEquals(totalTickets, root.path("totalTickets").asInt());
                assertEquals(totalPages, root.path("totalPages").asInt());
                JsonNode rows = root.path("tickets");
                assertTrue(rows.size() <= 50, "Each staff queue response must be bounded");
                for (JsonNode row : rows) {
                    assertTrue(seenIds.add(row.path("id").asInt()), "A ticket appeared on multiple pages");
                    if (row.path("title").asText().startsWith(titlePrefix)) foundFixtures++;
                }
            }
            assertEquals(totalTickets, seenIds.size(), "Every ticket must appear on exactly one page");
            assertEquals(FIXTURE_TICKETS, foundFixtures, "Every test ticket must be reachable past the old limit");

            HttpResponse<String> invalidPage = client.send(
                    HttpRequest.newBuilder(URI.create(base + "?page=0&pageSize=50"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalidPage.statusCode(), invalidPage.body());
            HttpResponse<String> invalidSize = client.send(
                    HttpRequest.newBuilder(URI.create(base + "?page=1&pageSize=101"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalidSize.statusCode(), invalidSize.body());
        } finally {
            if (server != null) server.stop();
            try (Connection admin = adminConnection()) {
                try (PreparedStatement statement = admin.prepareStatement(
                        "DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)")) {
                    statement.setString(1, emailPrefix + "%@example.test");
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = admin.prepareStatement(
                        "DELETE FROM ticket_history WHERE ticket_id IN (SELECT t.id FROM tickets t "
                                + "JOIN issues i ON i.id=t.issue_id WHERE i.title LIKE ?)")) {
                    statement.setString(1, titlePrefix + "%");
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = admin.prepareStatement(
                        "DELETE FROM tickets WHERE issue_id IN (SELECT id FROM issues WHERE title LIKE ?)")) {
                    statement.setString(1, titlePrefix + "%");
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = admin.prepareStatement("DELETE FROM issues WHERE title LIKE ?")) {
                    statement.setString(1, titlePrefix + "%");
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = admin.prepareStatement("DELETE FROM users WHERE email LIKE ?")) {
                    statement.setString(1, emailPrefix + "%@example.test");
                    statement.executeUpdate();
                }
            } finally {
                DBConnection.closePool();
            }
        }
    }

    private Connection adminConnection() throws java.sql.SQLException {
        String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
        String username = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
        String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
        if (url == null || url.isBlank()) url = System.getenv("CAMPUSOS_DB_URL");
        if (username == null || username.isBlank()) username = System.getenv("CAMPUSOS_DB_USERNAME");
        if (password == null || password.isBlank()) password = System.getenv("CAMPUSOS_DB_PASSWORD");
        return DriverManager.getConnection(url, username, password);
    }
}
