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
        assertEquals("http:", baseUri.getScheme(), "CI container target must use local HTTP");
        assertTrue(List.of("127.0.0.1", "localhost", "::1").contains(baseUri.getHost()),
                "Refusing to run this test against a remote API");
        String prefix = "container-load-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "-";
        String passwordHash = PasswordHasher.hash("ContainerLoad-Test-Only-2026");
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
