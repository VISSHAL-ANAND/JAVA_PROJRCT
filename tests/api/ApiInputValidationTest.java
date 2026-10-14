package api;

import model.Student;
import org.junit.jupiter.api.Test;
import security.AccessTokenService;
import security.RateLimiter;
import java.time.Duration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiInputValidationTest {
    @Test
    void loginRateLimitReturns429AfterConfiguredAttempts() throws Exception {
        AccessTokenService tokens = new AccessTokenService();
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(15), Duration.ofMinutes(15));
        ApiServer server = new ApiServer(0, tokens, limiter);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "http://127.0.0.1:" + server.getPort() + "/api/auth/login";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"missing@example.test\",\"password\":\"WrongPassword!123\"}"))
                    .build();
            HttpResponse<String> first = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(500, first.statusCode()); // Isolated smoke test has no database configured.
            HttpResponse<String> limited = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(429, limited.statusCode());
            assertEquals("900", limited.headers().firstValue("Retry-After").orElse(""));
            assertTrue(limited.body().contains("Too many login attempts"));
        } finally {
            server.stop();
        }
    }
    @Test
    void rejectsMalformedJsonAndNonJsonContentType() throws Exception {
        AccessTokenService tokens = new AccessTokenService();
        String token = tokens.issue(new Student(12, "Student", "student@example.test", "", "S12", "CSE"));
        ApiServer server = new ApiServer(0, tokens);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "http://127.0.0.1:" + server.getPort() + "/api/issues";
            HttpResponse<String> malformed = client.send(HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{not-json")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, malformed.statusCode());
            assertTrue(malformed.body().contains("Malformed JSON"));

            HttpResponse<String> wrongType = client.send(HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, wrongType.statusCode());
            assertTrue(wrongType.body().contains("Content-Type"));

            HttpResponse<String> jsonpType = client.send(HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/jsonp")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, jsonpType.statusCode());
            assertTrue(jsonpType.body().contains("Content-Type"));

            String oversizedBody = "{\"title\":\"test\",\"description\":\"" + "x".repeat(17 * 1024) + "\",\"category\":\"test\"}";
            HttpResponse<String> oversized = client.send(HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(oversizedBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, oversized.statusCode());
            assertTrue(oversized.body().contains("too large"));
        } finally {
            server.stop();
        }
    }
}
