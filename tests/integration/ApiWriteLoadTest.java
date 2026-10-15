package integration;

import api.ApiServer;
import dao.UserDAO;
import database.DBConnection;
import model.Student;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_WRITE_LOAD_TEST_ENABLED", matches = "true")
class ApiWriteLoadTest {
    private static final int REQUESTS = 1_000;

    @Test
    void concurrentIssueSubmissionsPersistTicketsAssignmentsHistoryAndNotifications() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emailPrefix = "write-load-" + suffix + "-";
        String titlePrefix = "Write Load " + suffix + " - ";
        String category = "LoadTestElectrical";
        UserDAO users = new UserDAO();
        AccessTokenService tokenService = new AccessTokenService();
        String hash = PasswordHasher.hash("WriteLoad-Test-Only-2026");
        List<String> tokens = new ArrayList<>(REQUESTS);
        int technicianId = 0;
        ApiServer server = null;
        try {
            technicianId = users.createUser("Load Technician " + suffix, emailPrefix + "tech@example.test",
                    hash, "TECHNICIAN", "WTECH" + suffix, "CSE", category);
            for (int i = 0; i < REQUESTS; i++) {
                String email = emailPrefix + i + "@example.test";
                int id = users.createUser("Load Reporter " + i, email, hash, "STUDENT",
                        "WLOAD" + suffix + i, "CSE", null);
                tokens.add(tokenService.issue(new Student(id, "Load Reporter " + i, email, "",
                        "WLOAD" + suffix + i, "CSE")));
            }
            server = new ApiServer(0);
            server.start();
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getPort() + "/api/issues");
            List<CompletableFuture<Void>> pending = new ArrayList<>(REQUESTS);
            long started = System.nanoTime();
            for (int i = 0; i < REQUESTS; i++) {
                String body = "{\"title\":\"" + titlePrefix + i
                        + "\",\"description\":\"Electrical fixture is broken\","
                        + "\"category\":\"" + category + "\",\"location\":\"Load Test Building\"}";
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(java.time.Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + tokens.get(i))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                final int number = i;
                pending.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            assertEquals(201, response.statusCode(), "Request " + number + ": " + response.body());
                            assertTrue(response.body().contains("\"ticketId\""));
                        }));
            }
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(90, TimeUnit.SECONDS);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            System.out.printf("CAMPUSOS write load smoke: requests=%d totalMs=%d%n", REQUESTS, elapsed);
            assertTrue(elapsed < 30_000, "1,000 concurrent issue submissions exceeded the 30-second CI budget");

            try (Connection c = DBConnection.getConnection()) {
                assertEquals(REQUESTS, count(c,
                        "SELECT COUNT(*) FROM issues WHERE title LIKE ?", titlePrefix + "%"));
                assertEquals(REQUESTS, count(c,
                        "SELECT COUNT(*) FROM tickets t JOIN issues i ON i.id=t.issue_id "
                                + "WHERE i.title LIKE ? AND t.status='ASSIGNED' AND t.assigned_technician_id=?",
                        titlePrefix + "%", technicianId));
                assertEquals(REQUESTS, count(c,
                        "SELECT COUNT(*) FROM ticket_history h JOIN tickets t ON t.id=h.ticket_id "
                                + "JOIN issues i ON i.id=t.issue_id WHERE i.title LIKE ?", titlePrefix + "%"));
                assertEquals(REQUESTS, count(c,
                        "SELECT COUNT(*) FROM notifications WHERE user_id=? "
                                + "AND message LIKE 'New ticket #% assigned to you.'", technicianId));
                assertEquals(REQUESTS, count(c,
                        "SELECT COUNT(*) FROM notifications WHERE message LIKE 'Ticket #% has been assigned to a technician.' "
                                + "AND user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                        emailPrefix + "%@example.test"));
            }
        } finally {
            if (server != null) server.stop();
            try (Connection admin = adminConnection()) {
                try (PreparedStatement s = admin.prepareStatement(
                        "DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)")) {
                    s.setString(1, emailPrefix + "%@example.test");
                    s.executeUpdate();
                }
                try (PreparedStatement s = admin.prepareStatement(
                        "DELETE FROM ticket_history WHERE ticket_id IN (SELECT t.id FROM tickets t "
                                + "JOIN issues i ON i.id=t.issue_id WHERE i.title LIKE ?)")) {
                    s.setString(1, titlePrefix + "%");
                    s.executeUpdate();
                }
                try (PreparedStatement s = admin.prepareStatement(
                        "DELETE FROM tickets WHERE issue_id IN (SELECT id FROM issues WHERE title LIKE ?)")) {
                    s.setString(1, titlePrefix + "%");
                    s.executeUpdate();
                }
                try (PreparedStatement s = admin.prepareStatement("DELETE FROM issues WHERE title LIKE ?")) {
                    s.setString(1, titlePrefix + "%");
                    s.executeUpdate();
                }
                try (PreparedStatement s = admin.prepareStatement("DELETE FROM users WHERE email LIKE ?")) {
                    s.setString(1, emailPrefix + "%@example.test");
                    s.executeUpdate();
                }
            } finally {
                DBConnection.closePool();
            }
        }
    }

    private static int count(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Integer id) s.setInt(i + 1, id);
                else s.setString(i + 1, (String) values[i]);
            }
            try (ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
                return r.getInt(1);
            }
        }
    }

    private Connection adminConnection() throws SQLException {
        String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
        String user = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
        String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
        if (url == null || url.isBlank()) url = System.getenv("CAMPUSOS_DB_URL");
        if (user == null || user.isBlank()) user = System.getenv("CAMPUSOS_DB_USERNAME");
        if (password == null) password = System.getenv("CAMPUSOS_DB_PASSWORD");
        if (url == null || user == null || password == null) throw new SQLException("Admin DB settings missing");
        return DriverManager.getConnection(url, user, password);
    }
}
