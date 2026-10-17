package api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.Admin;
import model.Student;
import dao.UserDAO;
import org.junit.jupiter.api.Test;
import security.AccessTokenService;
import security.LoginAttemptStore;
import security.RateLimiter;
import security.TokenRevocationStore;
import service.AuthService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ApiMetricsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void operationalMetricsAreAdminOnlyAndExposeBoundedPoolAndLatencyCounters() throws Exception {
        AccessTokenService tokens = new AccessTokenService();
        Map<Integer, String> roles = Map.of(7001, "ADMIN", 7002, "STUDENT");
        UserDAO users = new UserDAO() {
            @Override public Optional<String> findRoleById(int userId) {
                return Optional.ofNullable(roles.get(userId));
            }
        };
        ApiServer server = new ApiServer(0, tokens,
                new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                users, new AuthService(users), TokenRevocationStore.noOp(), LoginAttemptStore.noOp());
        String adminToken = tokens.issue(new Admin(7001, "Metrics Admin", "metrics-admin@example.test", ""));
        String studentToken = tokens.issue(new Student(7002, "Metrics Student", "metrics-student@example.test", "", "S7002", "CSE"));
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getPort() + "/api/admin/metrics");

            HttpResponse<String> anonymous = client.send(HttpRequest.newBuilder(endpoint).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, anonymous.statusCode());

            HttpResponse<String> student = client.send(HttpRequest.newBuilder(endpoint)
                            .header("Authorization", "Bearer " + studentToken).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, student.statusCode());

            HttpResponse<String> first = client.send(HttpRequest.newBuilder(endpoint)
                            .header("Authorization", "Bearer " + adminToken).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, first.statusCode(), first.body());
            JsonNode metrics = JSON.readTree(first.body()).path("metrics");
            assertEquals("this_instance", metrics.path("scope").asText());
            assertTrue(metrics.path("uptimeSeconds").isNumber());
            assertTrue(metrics.path("http").path("workerCapacity").asInt() >= 4);
            assertTrue(metrics.path("http").path("workerCount").asInt() >= 1);
            assertTrue(metrics.path("http").path("queueRemainingCapacity").asInt() >= 0);
            assertTrue(metrics.path("database").path("initialized").isBoolean());
            assertTrue(metrics.path("requests").path("latencyBuckets").path("gt2000ms").isNumber());
            assertFalse(first.body().toLowerCase().contains("password"));
            assertFalse(first.body().toLowerCase().contains("jdbc:"));

            URI health = URI.create("http://127.0.0.1:" + server.getPort() + "/api/health");
            List<CompletableFuture<HttpResponse<String>>> concurrentHealthRequests = IntStream.range(0, 250)
                    .mapToObj(i -> client.sendAsync(HttpRequest.newBuilder(health).GET().build(),
                            HttpResponse.BodyHandlers.ofString()))
                    .toList();
            CompletableFuture.allOf(concurrentHealthRequests.toArray(CompletableFuture[]::new))
                    .get(30, TimeUnit.SECONDS);
            for (CompletableFuture<HttpResponse<String>> response : concurrentHealthRequests) {
                assertEquals(200, response.get().statusCode());
            }

            HttpResponse<String> second = client.send(HttpRequest.newBuilder(endpoint)
                            .header("Authorization", "Bearer " + adminToken).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, second.statusCode(), second.body());
            assertTrue(JSON.readTree(second.body()).path("metrics").path("requests")
                    .path("completedResponses").asLong() >= 253,
                    "Metrics should count the preceding anonymous, student, admin, and 250 concurrent health requests");
        } finally {
            server.stop();
        }
    }
}
