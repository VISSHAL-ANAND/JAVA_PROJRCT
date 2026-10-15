package api;

import dao.UserDAO;
import database.DBConnection;
import model.Student;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI load smoke test for an authenticated, database-backed read path.
 * Uses ephemeral PostgreSQL users and removes them after the run.
 */
class ApiDatabaseLoadTest {
    private static final int DISTINCT_USERS = 1_000;
    private static final int CONCURRENT_REQUESTS = 1_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void servesOneThousandConcurrentAuthenticatedDatabaseRequests() throws Exception {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("CAMPUSOS_DB_LOAD_TEST_ENABLED")),
                "Enable only in the PostgreSQL integration CI job");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserDAO users = new UserDAO();
        AccessTokenService tokenService = new AccessTokenService();
        String passwordHash = PasswordHasher.hash("LoadTest!Only-Not-A-Real-Account-2026");
        List<Integer> userIds = new ArrayList<>(DISTINCT_USERS);
        List<String> tokens = new ArrayList<>(DISTINCT_USERS);
        ApiServer server = null;
        try {
            for (int i = 0; i < DISTINCT_USERS; i++) {
                String email = "db-load-" + suffix + "-" + i + "@example.test";
                int id = users.createUser("Load Test User " + i, email, passwordHash,
                        "STUDENT", "LOAD" + suffix + i, "CSE", null);
                userIds.add(id);
                tokens.add(tokenService.issue(new Student(
                        id, "Load Test User " + i, email, "", "LOAD" + suffix + i, "CSE")));
            }

            server = new ApiServer(0);
            server.start();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String base = "http://127.0.0.1:" + server.getPort();
            List<Long> latencyMillis = Collections.synchronizedList(new ArrayList<>(CONCURRENT_REQUESTS));
            List<CompletableFuture<Void>> requests = new ArrayList<>(CONCURRENT_REQUESTS);
            long startedAt = System.nanoTime();

            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                int userIndex = i % DISTINCT_USERS;
                long requestStartedAt = System.nanoTime();
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(base + "/api/tickets/reporter/" + userIds.get(userIndex)))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + tokens.get(userIndex))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                requests.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            latencyMillis.add(TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - requestStartedAt));
                            assertEquals(200, response.statusCode(),
                                    "Authenticated reporter query should succeed: " + response.body());
                            assertTrue(response.body().contains("\"success\":true"));
                            assertTrue(response.body().contains("\"tickets\":[]"));
                        }));
            }

            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                    .get(REQUEST_TIMEOUT.toSeconds() + 15, TimeUnit.SECONDS);
            long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            List<Long> sorted = new ArrayList<>(latencyMillis);
            Collections.sort(sorted);
            long p50 = percentile(sorted, 50);
            long p95 = percentile(sorted, 95);
            long max = sorted.get(sorted.size() - 1);

            System.out.printf(
                    "CAMPUSOS database load smoke: distinctUsers=%d requests=%d concurrency=%d totalMs=%d p50Ms=%d p95Ms=%d maxMs=%d poolSize=%s%n",
                    DISTINCT_USERS, sorted.size(), CONCURRENT_REQUESTS, totalMillis, p50, p95, max,
                    System.getenv().getOrDefault("CAMPUSOS_DB_POOL_SIZE", "10 (default)"));

            assertEquals(CONCURRENT_REQUESTS, sorted.size(), "All requests must finish");
            assertTrue(totalMillis < 45_000,
                    "1,000 authenticated DB requests exceeded the 45-second CI smoke-test budget");
            assertTrue(p95 < 30_000,
                    "95th-percentile authenticated DB latency exceeded the 30-second CI smoke-test budget");
        } finally {
            if (server != null) server.stop();
            try {
                String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
                String username = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
                String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
                if (url != null && username != null && password != null) {
                    try (Connection connection = DriverManager.getConnection(url, username, password);
                         PreparedStatement statement = connection.prepareStatement(
                                 "DELETE FROM public.users WHERE email LIKE ?")) {
                        statement.setString(1, "db-load-" + suffix + "-%@example.test");
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
