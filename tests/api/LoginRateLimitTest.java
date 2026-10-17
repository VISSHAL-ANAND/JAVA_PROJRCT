package api;

import dao.UserDAO;
import exception.AuthenticationException;
import exception.AuthenticationUnavailableException;
import model.Student;
import model.User;
import org.junit.jupiter.api.Test;
import security.AccessTokenService;
import security.RateLimiter;
import security.TokenRevocationStore;
import security.LoginAttemptStore;
import service.AuthService;
import security.LoginAttemptStore;
import security.TokenRevocationStore;
import java.sql.SQLException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginRateLimitTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String LOGIN_BODY = """
            {"email":"student@example.test","password":"correct-password"}
            """;

    @Test
    void successfulLoginsFromOneSharedIpDoNotAccumulateTowardLockout() throws Exception {
        ApiServer server = startServer(true);
        try {
            for (int attempt = 1; attempt <= 6; attempt++) {
                HttpResponse<String> response = login(server);
                assertEquals(200, response.statusCode(),
                        "Successful login " + attempt + " should not be blocked by prior successes");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void repeatedFailedLoginsFromOneIpAreStillThrottled() throws Exception {
        ApiServer server = startServer(false);
        try {
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertEquals(401, login(server).statusCode(),
                        "The first five invalid credentials should receive the generic authentication response");
            }
            HttpResponse<String> blocked = login(server);
            assertEquals(429, blocked.statusCode(), "The sixth consecutive failure should be rate limited");
            assertEquals("900", blocked.headers().firstValue("Retry-After").orElse(null));
        } finally {
            server.stop();
        }
    }

    @Test
    void failedAttemptsForOneAccountDoNotBlockAnotherAccountBehindSameProxy() throws Exception {
        AuthService authService = new AuthService(new UserDAO()) {
            @Override
            public User authenticate(String email, String password) throws AuthenticationException {
                if ("blocked@example.test".equals(email)) {
                    throw new AuthenticationException("Invalid email or password");
                }
                return new Student(2, "Other Student", email, "", "TEST-2", "CSE");
            }
        };
        ApiServer server = new ApiServer(0, new AccessTokenService(),
                new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new UserDAO(), authService, TokenRevocationStore.noOp(), LoginAttemptStore.noOp());
        server.start();
        try {
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertEquals(401, login(server, "blocked@example.test").statusCode());
            }
            assertEquals(429, login(server, "blocked@example.test").statusCode(),
                    "The targeted account/address bucket should still be throttled");
            assertEquals(200, login(server, "other@example.test").statusCode(),
                    "Another account sharing the same apparent source IP must remain able to sign in");
        } finally {
            server.stop();
        }
    }

    @Test
    void databaseFailureReturns503AndDoesNotBecomeCredentialLockout() throws Exception {
        UserDAO unavailable = new UserDAO() {
            @Override
            public java.util.Optional<model.AuthUser> findForAuthentication(String email) throws SQLException {
                throw new SQLException("simulated database outage");
            }
        };
        AuthService authService = new AuthService(unavailable,
                new RateLimiter(5, Duration.ofMinutes(5), Duration.ofMinutes(10)));
        ApiServer server = new ApiServer(0, new AccessTokenService(),
                new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                unavailable, authService, TokenRevocationStore.noOp(), LoginAttemptStore.noOp());
        server.start();
        try {
            for (int attempt = 0; attempt < 7; attempt++) {
                HttpResponse<String> response = login(server, "database-outage@example.test");
                assertEquals(503, response.statusCode(),
                        "Database failures must be reported as unavailable, not invalid credentials or 429");
                assertEquals("Authentication service temporarily unavailable",
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body())
                                .path("message").asText());
            }
        } finally {
            server.stop();
        }
    }

    private ApiServer startServer(boolean authenticateSuccessfully) throws IOException {
        AuthService authService = new AuthService(new UserDAO()) {
            @Override
            public User authenticate(String email, String password) throws AuthenticationException {
                if (!authenticateSuccessfully) {
                    throw new AuthenticationException("Invalid email or password");
                }
                return new Student(1, "Test Student", email, "", "TEST-1", "CSE");
            }
        };
        ApiServer server = new ApiServer(0, new AccessTokenService(),
                new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new UserDAO(), authService, TokenRevocationStore.noOp(), LoginAttemptStore.noOp());
        server.start();
        return server;
    }

    private HttpResponse<String> login(ApiServer server) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(LOGIN_BODY))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(ApiServer server, String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"correct-password\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
