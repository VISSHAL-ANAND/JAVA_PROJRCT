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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_MIXED_LOAD_TEST_ENABLED", matches = "true")
class MixedWorkloadScaleTest {
    private static final int TOTAL_USERS = 1_000;
    private static final int READ_TICKET_REQUESTS = 400;
    private static final int READ_NOTIFICATION_REQUESTS = 400;
    private static final int WRITE_ISSUE_REQUESTS = 200;
    private static final int TOTAL_REQUESTS =
            READ_TICKET_REQUESTS + READ_NOTIFICATION_REQUESTS + WRITE_ISSUE_REQUESTS;
    private static final int ROUNDS = 5;

    @Test
    void sustainedMixedWorkloadRemainsCorrectAcrossFiveThousandRequests() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emailPrefix = "mixed-load-" + suffix + "-";
        String titlePrefix = "Mixed Load " + suffix + " - ";
        String category = "MixedLoadElectrical";
        String passwordHash = PasswordHasher.hash("MixedLoad-Test-Only-2026");
        UserDAO users = new UserDAO();
        AccessTokenService tokenService = new AccessTokenService();
        List<Integer> userIds = new ArrayList<>(TOTAL_USERS);
        List<String> tokens = new ArrayList<>(TOTAL_USERS);
        int technicianId = 0;
        ApiServer server = null;

        try {
            technicianId = users.createUser("Mixed Load Technician " + suffix,
                    emailPrefix + "tech@example.test", passwordHash,
                    "TECHNICIAN", "MTECH" + suffix, "CSE", category);
            for (int i = 0; i < TOTAL_USERS; i++) {
                String email = emailPrefix + i + "@example.test";
                int id = users.createUser("Mixed Load User " + i, email, passwordHash,
                        "STUDENT", "MLOAD" + suffix + i, "CSE", null);
                userIds.add(id);
                tokens.add(tokenService.issue(new Student(id, "Mixed Load User " + i, email,
                        "", "MLOAD" + suffix + i, "CSE")));
            }

            server = new ApiServer(0);
            server.start();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String base = "http://127.0.0.1:" + server.getPort();
            URI issueEndpoint = URI.create(base + "/api/issues");
            for (int round = 1; round <= ROUNDS; round++) {
                List<CompletableFuture<Void>> pending = new ArrayList<>(TOTAL_REQUESTS);
                String roundTitlePrefix = titlePrefix + "round-" + round + " - ";
                long startedAt = System.nanoTime();

                for (int i = 0; i < READ_TICKET_REQUESTS; i++) {
                    pending.add(send(client, HttpRequest.newBuilder(URI.create(
                                    base + "/api/tickets/reporter/" + userIds.get(i)))
                            .timeout(Duration.ofSeconds(45))
                            .header("Authorization", "Bearer " + tokens.get(i))
                            .header("Accept", "application/json").GET().build(), 200, "\"tickets\":"));
                }
                for (int i = 0; i < READ_NOTIFICATION_REQUESTS; i++) {
                    int userIndex = READ_TICKET_REQUESTS + i;
                    pending.add(send(client, HttpRequest.newBuilder(URI.create(base + "/api/notifications/me"))
                            .timeout(Duration.ofSeconds(45))
                            .header("Authorization", "Bearer " + tokens.get(userIndex))
                            .header("Accept", "application/json").GET().build(), 200, "\"success\":true"));
                }
                for (int i = 0; i < WRITE_ISSUE_REQUESTS; i++) {
                    int userIndex = READ_TICKET_REQUESTS + READ_NOTIFICATION_REQUESTS + i;
                    String body = "{\"title\":\"" + roundTitlePrefix + i
                            + "\",\"description\":\"Electrical fixture needs repair\","
                            + "\"category\":\"" + category + "\","
                            + "\"location\":\"Mixed Load Building\"}";
                    pending.add(send(client, HttpRequest.newBuilder(issueEndpoint)
                            .timeout(Duration.ofSeconds(45))
                            .header("Authorization", "Bearer " + tokens.get(userIndex))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), 201, "\"ticketId\":"));
                }

                CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                        .get(90, TimeUnit.SECONDS);
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                System.out.printf("CAMPUSOS mixed load soak: round=%d/%d users=%d concurrentRequests=%d "
                                + "ticketReads=%d notificationReads=%d issueWrites=%d totalMs=%d%n",
                        round, ROUNDS, TOTAL_USERS, TOTAL_REQUESTS, READ_TICKET_REQUESTS,
                        READ_NOTIFICATION_REQUESTS, WRITE_ISSUE_REQUESTS, elapsed);
                assertTrue(elapsed < 20_000,
                        "Mixed 1,000-request workload exceeded the 20-second budget in round " + round);
            }

            try (Connection connection = DBConnection.getConnection()) {
                int expectedWrites = WRITE_ISSUE_REQUESTS * ROUNDS;
                assertEquals(expectedWrites, count(connection,
                        "SELECT COUNT(*) FROM issues WHERE title LIKE ?", titlePrefix + "%"));
                assertEquals(expectedWrites, count(connection,
                        "SELECT COUNT(*) FROM tickets t JOIN issues i ON i.id=t.issue_id "
                                + "WHERE i.title LIKE ? AND t.status='ASSIGNED' AND t.assigned_technician_id=?",
                        titlePrefix + "%", technicianId));
                assertEquals(expectedWrites, count(connection,
                        "SELECT COUNT(*) FROM ticket_history h JOIN tickets t ON t.id=h.ticket_id "
                                + "JOIN issues i ON i.id=t.issue_id WHERE i.title LIKE ?", titlePrefix + "%"));
                assertEquals(expectedWrites, count(connection,
                        "SELECT COUNT(*) FROM notifications WHERE user_id=? "
                                + "AND message LIKE 'New ticket #% assigned to you.'", technicianId));
                assertEquals(expectedWrites, count(connection,
                        "SELECT COUNT(*) FROM notifications WHERE message LIKE "
                                + "'Ticket #% has been assigned to a technician.' AND user_id IN "
                                + "(SELECT id FROM users WHERE email LIKE ?)", emailPrefix + "%@example.test"));
            }
        } finally {
            if (server != null) server.stop();
            try (Connection admin = adminConnection()) {
                try (PreparedStatement statement = admin.prepareStatement(
                        "DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)")) {
                    statement.setString(1, emailPrefix + "%@example.test");
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = admin.prepareStatement(
                        "DELETE FROM notifications WHERE user_id=? AND message LIKE 'New ticket #% assigned to you.'")) {
                    statement.setInt(1, technicianId);
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

    private static CompletableFuture<Void> send(HttpClient client, HttpRequest request,
                                                int expectedStatus, String expectedBody) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    assertEquals(expectedStatus, response.statusCode(), response.body());
                    assertTrue(response.body().contains(expectedBody),
                            "Expected response marker " + expectedBody + " but got: " + response.body());
                });
    }

    private static int count(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Integer id) statement.setInt(i + 1, id);
                else statement.setString(i + 1, (String) values[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private Connection adminConnection() throws SQLException {
        String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
        String username = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
        String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
        if (url == null || url.isBlank()) url = System.getenv("CAMPUSOS_DB_URL");
        if (username == null || username.isBlank()) username = System.getenv("CAMPUSOS_DB_USERNAME");
        if (password == null || password.isBlank()) password = System.getenv("CAMPUSOS_DB_PASSWORD");
        if (url == null || username == null || password == null)
            throw new SQLException("Admin database connection settings are missing");
        return DriverManager.getConnection(url, username, password);
    }
}
