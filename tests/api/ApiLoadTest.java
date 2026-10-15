package api;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bounded CI load smoke test for the HTTP server.
 *
 * This deliberately targets the dependency-free liveness endpoint: it measures HTTP
 * concurrency and worker-pool behavior, not database throughput or application-wide
 * capacity. Run only in the dedicated load-test job.
 */
class ApiLoadTest {
    private static final int CONCURRENT_REQUESTS = 1_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    @Test
    void servesOneThousandConcurrentHealthRequestsWithoutErrors() throws Exception {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("CAMPUSOS_LOAD_TEST_ENABLED")),
                "Enable only in the dedicated CI load-test job");

        ApiServer server = new ApiServer(0);
        server.start();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getPort() + "/api/health");
            List<Long> latencyMillis = Collections.synchronizedList(new ArrayList<>(CONCURRENT_REQUESTS));
            List<CompletableFuture<Void>> requests = new ArrayList<>(CONCURRENT_REQUESTS);
            long startedAt = System.nanoTime();

            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                long requestStartedAt = System.nanoTime();
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                requests.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            latencyMillis.add(TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - requestStartedAt));
                            assertEquals(200, response.statusCode(),
                                    "Liveness request should succeed: " + response.body());
                            assertTrue(response.body().contains("\"status\":\"ok\""));
                        }));
            }

            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                    .get(REQUEST_TIMEOUT.toSeconds() + 10, TimeUnit.SECONDS);
            long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            List<Long> sorted = new ArrayList<>(latencyMillis);
            Collections.sort(sorted);
            long p50 = percentile(sorted, 50);
            long p95 = percentile(sorted, 95);
            long max = sorted.get(sorted.size() - 1);

            System.out.printf(
                    "CAMPUSOS load smoke: requests=%d concurrency=%d totalMs=%d p50Ms=%d p95Ms=%d maxMs=%d%n",
                    sorted.size(), CONCURRENT_REQUESTS, totalMillis, p50, p95, max);

            assertEquals(CONCURRENT_REQUESTS, sorted.size(), "All requests must finish");
            assertTrue(totalMillis < 30_000,
                    "1,000 concurrent liveness requests exceeded the 30-second CI smoke-test budget");
            assertTrue(p95 < 20_000,
                    "95th-percentile liveness latency exceeded the 20-second CI smoke-test budget");
        } finally {
            server.stop();
        }
    }

    private static long percentile(List<Long> sorted, int percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return sorted.get(index);
    }
}
