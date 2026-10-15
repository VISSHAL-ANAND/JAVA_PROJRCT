package integration;

import api.ApiServer;
import dao.UserDAO;
import database.DBConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import security.PasswordHasher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies simultaneous real password verification/login for distinct accounts behind one shared IP. */
@EnabledIfEnvironmentVariable(named = "CAMPUSOS_LOGIN_LOAD_TEST_ENABLED", matches = "true")
class ApiLoginLoadTest {
    private static final int CONCURRENT_LOGINS = 1_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    @Test
    void handlesOneThousandConcurrentSuccessfulLoginsFromOneSharedSourceAddress() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emailPrefix = "login-load-" + suffix + "-";
        String plainPassword = "ConcurrentLogin!Only-2026";
        String passwordHash = PasswordHasher.hash(plainPassword);
        List<String> emails = new ArrayList<>(CONCURRENT_LOGINS);
        UserDAO users = new UserDAO();
        ApiServer server = null;

        try {
            for (int i = 0; i < CONCURRENT_LOGINS; i++) {
                String email = emailPrefix + i + "@example.test";
                users.createUser("Login Load User " + i, email, passwordHash,
                        "STUDENT", "LLOGIN" + suffix + i, "CSE", null);
                emails.add(email);
            }

            server = new ApiServer(0);
            server.start();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getPort() + "/api/auth/login");
            List<Long> latencyMillis = Collections.synchronizedList(new ArrayList<>(CONCURRENT_LOGINS));
            List<CompletableFuture<Void>> requests = new ArrayList<>(CONCURRENT_LOGINS);
            long startedAt = System.nanoTime();

            for (int i = 0; i < CONCURRENT_LOGINS; i++) {
                String email = emails.get(i);
                String body = "{\"email\":\"" + email + "\",\"password\":\"" + plainPassword + "\"}";
                long requestStartedAt = System.nanoTime();
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                final int requestNumber = i;
                requests.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            latencyMillis.add(TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - requestStartedAt));
                            assertEquals(200, response.statusCode(),
                                    "Concurrent login " + requestNumber + " failed: " + response.body());
                            assertTrue(response.body().contains("\"success\":true"));
                            assertTrue(response.body().contains("\"token\""));
                        }));
            }

            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                    .get(REQUEST_TIMEOUT.toSeconds() + 30, TimeUnit.SECONDS);
            long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            List<Long> sorted = new ArrayList<>(latencyMillis);
            Collections.sort(sorted);
            long p50 = percentile(sorted, 50);
            long p95 = percentile(sorted, 95);
            long max = sorted.get(sorted.size() - 1);
            System.out.printf("CAMPUSOS login load smoke: distinctAccounts=%d concurrentLogins=%d totalMs=%d p50Ms=%d p95Ms=%d maxMs=%d%n",
                    CONCURRENT_LOGINS, sorted.size(), totalMillis, p50, p95, max);

            assertEquals(CONCURRENT_LOGINS, sorted.size(), "Every login must finish");
            assertTrue(totalMillis < 30_000,
                    "1,000 simultaneous password-verification logins exceeded the 30-second CI budget");
            assertTrue(p95 < 20_000,
                    "95th-percentile login latency exceeded the 20-second CI budget");
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

    private static long percentile(List<Long> sorted, int percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return sorted.get(index);
    }
}
