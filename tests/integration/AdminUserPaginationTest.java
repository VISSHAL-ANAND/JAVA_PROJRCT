package integration;

import api.ApiServer;
import dao.UserDAO;
import database.DBConnection;
import model.Admin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import security.AccessTokenService;
import security.PasswordHasher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_ADMIN_PAGINATION_TEST_ENABLED", matches = "true")
class AdminUserPaginationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int FIXTURE_USERS = 1_000;

    @Test
    void adminCanPageThroughEveryUserWithoutDuplicatesOrTruncation() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emailPrefix = "admin-page-" + suffix + "-";
        String hash = PasswordHasher.hash("Pagination!Test-Only-2026");
        UserDAO users = new UserDAO();
        Set<Integer> fixtureIds = new HashSet<>();
        int adminId = 0;
        ApiServer server = null;
        try {
            adminId = users.createUser("Pagination Admin " + suffix, emailPrefix + "admin@example.test",
                    hash, "ADMIN", null, "CSE", null);
            fixtureIds.add(adminId);
            for (int i = 0; i < FIXTURE_USERS; i++) {
                int id = users.createUser("Pagination User " + i, emailPrefix + i + "@example.test",
                        hash, "STUDENT", "PAGE" + suffix + i, "CSE", null);
                fixtureIds.add(id);
            }

            String token = new AccessTokenService().issue(
                    new Admin(adminId, "Pagination Admin " + suffix, emailPrefix + "admin@example.test", ""));
            server = new ApiServer(0);
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getPort() + "/api/admin/users";
            int totalUsers = users.countUsers();
            int totalPages = Math.max(1, (totalUsers + 49) / 50);
            assertTrue(totalPages >= 3, "Fixture should span at least three pages");

            Set<Integer> seen = new HashSet<>();
            for (int page = 1; page <= totalPages; page++) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(base + "?page=" + page + "&pageSize=50"))
                                .header("Authorization", "Bearer " + token)
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                JsonNode root = JSON.readTree(response.body());
                assertTrue(root.path("success").asBoolean());
                assertEquals(page, root.path("page").asInt());
                assertEquals(50, root.path("pageSize").asInt());
                assertEquals(totalUsers, root.path("totalUsers").asInt());
                assertEquals(totalPages, root.path("totalPages").asInt());
                JsonNode rows = root.path("users");
                assertTrue(rows.size() <= 50, "The API must keep each response bounded");
                for (JsonNode row : rows) {
                    assertTrue(seen.add(row.path("id").asInt()),
                            "An account appeared on multiple pages");
                }
            }
            assertEquals(totalUsers, seen.size(), "Every user must appear on exactly one page");
            assertTrue(seen.containsAll(fixtureIds), "All 1,001 test accounts must be reachable");

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
            try {
                String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
                String username = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
                String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
                if (url == null || username == null || password == null) {
                    url = System.getenv("CAMPUSOS_DB_URL");
                    username = System.getenv("CAMPUSOS_DB_USERNAME");
                    password = System.getenv("CAMPUSOS_DB_PASSWORD");
                }
                if (url != null && username != null && password != null) {
                    try (Connection connection = DriverManager.getConnection(url, username, password);
                         PreparedStatement statement = connection.prepareStatement(
                                 "DELETE FROM users WHERE email LIKE ?")) {
                        statement.setString(1, emailPrefix + "%@example.test");
                        statement.executeUpdate();
                    }
                }
            } finally {
                DBConnection.closePool();
            }
        }
    }
}
