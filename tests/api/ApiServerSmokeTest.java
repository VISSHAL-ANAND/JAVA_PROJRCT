package api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerSmokeTest {
    @Test
    void healthEndpointRespondsAndProtectedRoutesRejectAnonymousRequests() throws Exception {
        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            String base = "http://127.0.0.1:" + server.getPort();

            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/health"))
                            .timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"ok\""));

            HttpResponse<String> issue = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/issues"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"test\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, issue.statusCode());

            HttpResponse<String> tickets = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/reporter/1")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, tickets.statusCode());
        } finally {
            server.stop();
        }
    }
}
