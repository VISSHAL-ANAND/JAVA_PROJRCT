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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_CONTAINER_LOAD_TEST_ENABLED", matches = "true")
class ContainerApiLoadTest {
    private static final int CONCURRENT_USERS = 1_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    @Test
    void productionContainerServesOneThousandAuthenticatedDatabaseBackedUsers() throws Exception {
        String base = System.getenv("CAMPUSOS_CONTAINER_BASE_URL");
        URI baseUri = URI.create(base == null ? "" : base);
        assertEquals("http", baseUri.getScheme(), "CI container target must use local HTTP");
        assertTrue(List.of("127.0.0.1", "localhost", "::1").contains(baseUri.getHost()),
                "Refusing to run this test against a remote API");
        String prefix = "container-load-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "-";
        String plainPassword = "ContainerLoad-Test-Only-2026";
        String passwordHash = PasswordHasher.hash(plainPassword);
        UserDAO users = new UserDAO();
        AccessTokenService tokenService = new AccessTokenService();
        List<Integer> userIds = new ArrayList<>(CONCURRENT_USERS);
        List<String> tokens = new ArrayList<>(CONCURRENT_USERS);

        try {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                String email = prefix + i + "@example.test";
                int id = users.createUser("Container Load User " + i, email, passwordHash,
                        "STUDENT", "CL" + prefix.substring(prefix.length() - 12) + i, "CSE", null);
                userIds.add(id);
                tokens.add(tokenService.issue(new Student(id, "Container Load User " + i,
                        email, "", "CL" + prefix.substring(prefix.length() - 12) + i, "CSE")));
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            List<Long> latencies = Collections.synchronizedList(new ArrayList<>(CONCURRENT_USERS));
            List<String> failures = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<Void>> pending = new ArrayList<>(CONCURRENT_USERS);
            long started = System.nanoTime();

            for (int i = 0; i < CONCURRENT_USERS; i++) {
                final int index = i;
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(base + "/api/tickets/reporter/" + userIds.get(i)))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + tokens.get(i))
                        .header("Accept", "application/json")
                        .GET().build();
                long requestStarted = System.nanoTime();
                pending.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStarted));
                            if (response.statusCode() != 200 || !response.body().contains("\"tickets\":")) {
                                failures.add("userIndex=" + index + " HTTP " + response.statusCode()
                                        + " body=" + response.body().substring(0, Math.min(160, response.body().length())));
                            }
                        }));
            }
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                    .get(REQUEST_TIMEOUT.toSeconds() + 30, TimeUnit.SECONDS);
            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            long p50 = percentile(sorted, 50);
            long p95 = percentile(sorted, 95);
            long max = sorted.isEmpty() ? Long.MAX_VALUE : sorted.get(sorted.size() - 1);
            System.out.printf("CAMPUSOS production-container authenticated load: users=%d requests=%d succeeded=%d failed=%d totalMs=%d p50Ms=%d p95Ms=%d maxMs=%d%n",
                    CONCURRENT_USERS, sorted.size(), sorted.size() - failures.size(), failures.size(), totalMs, p50, p95, max);

            assertEquals(CONCURRENT_USERS, sorted.size(), "Every request must complete");
            assertTrue(failures.isEmpty(), "Authenticated container requests failed: " + failures.stream().limit(10).toList());
            assertTrue(totalMs < 30_000, "1,000 authenticated container requests exceeded the 30-second CI budget");
            assertTrue(p95 < 15_000, "Authenticated container request p95 exceeded the 15-second CI smoke budget");

            // Exercise real database-backed authentication through the same production container,
            // not only signed-token generation in the test process.
            List<Long> loginLatencies = Collections.synchronizedList(new ArrayList<>(CONCURRENT_USERS));
            List<String> loginFailures = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<Void>> loginPending = new ArrayList<>(CONCURRENT_USERS);
            URI loginEndpoint = URI.create(base + "/api/auth/login");
            long loginStarted = System.nanoTime();
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                final int index = i;
                String email = prefix + i + "@example.test";
                String body = "{\"email\":\"" + email + "\",\"password\":\"" + plainPassword + "\"}";
                HttpRequest request = HttpRequest.newBuilder(loginEndpoint)
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                long requestStarted = System.nanoTime();
                loginPending.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            loginLatencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStarted));
                            if (response.statusCode() != 200
                                    || !response.body().contains("\"success\":true")
                                    || !response.body().contains("\"token\"")) {
                                loginFailures.add("userIndex=" + index + " HTTP " + response.statusCode()
                                        + " body=" + response.body().substring(0, Math.min(160, response.body().length())));
                            }
                        }));
            }
            CompletableFuture.allOf(loginPending.toArray(CompletableFuture[]::new))
                    .get(60, TimeUnit.SECONDS);
            long loginTotalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - loginStarted);
            List<Long> sortedLoginLatencies = new ArrayList<>(loginLatencies);
            Collections.sort(sortedLoginLatencies);
            long loginP50 = percentile(sortedLoginLatencies, 50);
            long loginP95 = percentile(sortedLoginLatencies, 95);
            long loginMax = sortedLoginLatencies.isEmpty()
                    ? Long.MAX_VALUE : sortedLoginLatencies.get(sortedLoginLatencies.size() - 1);
            System.out.printf("CAMPUSOS production-container login load: users=%d requests=%d succeeded=%d failed=%d totalMs=%d p50Ms=%d p95Ms=%d maxMs=%d%n",
                    CONCURRENT_USERS, sortedLoginLatencies.size(),
                    sortedLoginLatencies.size() - loginFailures.size(), loginFailures.size(),
                    loginTotalMs, loginP50, loginP95, loginMax);

            assertEquals(CONCURRENT_USERS, sortedLoginLatencies.size(), "Every container login must complete");
            assertTrue(loginFailures.isEmpty(),
                    "Production-container logins failed: " + loginFailures.stream().limit(10).toList());
            assertTrue(loginTotalMs < 30_000, "1,000 simultaneous container logins exceeded the 30-second CI budget");
            assertTrue(loginP95 < 20_000, "Production-container login p95 exceeded the 20-second CI smoke budget");
        } finally {
            try {
                String adminUrl = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
                String adminUsername = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
                String adminPassword = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
                if (adminUrl == null || adminUsername == null || adminPassword == null) {
                    throw new IllegalStateException("Dedicated CI admin cleanup connection is required");
                }
                try (Connection connection = DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE email LIKE ?")) {
                    statement.setString(1, prefix + "%@example.test");
                    statement.executeUpdate();
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
