package api;

import org.junit.jupiter.api.Test;
import model.Student;
import dao.UserDAO;
import java.util.Optional;
import security.AccessTokenService;
import security.TokenRevocationStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerSmokeTest {
    @Test
    void studentTokenCannotAccessAdminOrStaffOperations() throws Exception {
        AccessTokenService tokens = new AccessTokenService();
        String token = tokens.issue(new Student(12, "Student", "student@example.test", "", "S12", "CSE"));
        ApiServer server = new ApiServer(0, tokens, new UserDAO() {
            @Override public Optional<String> findRoleById(int userId) { return Optional.of("STUDENT"); }
        }, TokenRevocationStore.noOp());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getPort();

            HttpResponse<String> admin = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, admin.statusCode());

            HttpResponse<String> staff = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, staff.statusCode());

            HttpResponse<String> emergency = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/emergencies"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"test\",\"message\":\"test\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, emergency.statusCode());
            
            HttpResponse<String> staffStatus = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets/999/status"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"status\":\"RESOLVED\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, staffStatus.statusCode());

            HttpResponse<String> assignTicket = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/tickets/999/assign"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"staffId\":13}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, assignTicket.statusCode());

            HttpResponse<String> changeAvailability = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users/13/availability"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"available\":false}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, changeAvailability.statusCode());

            HttpResponse<String> listStaff = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/staff"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, listStaff.statusCode());
        } finally {
            server.stop();
        }
    }


    @Test
    void authenticatedUserCannotReadAnotherUsersTickets() throws Exception {
        AccessTokenService tokens = new AccessTokenService();
        String token = tokens.issue(new Student(12, "Student", "student@example.test", "", "S12", "CSE"));
        ApiServer server = new ApiServer(0, tokens, new UserDAO() {
            @Override public Optional<String> findRoleById(int userId) { return Optional.of("STUDENT"); }
        }, TokenRevocationStore.noOp());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "http://127.0.0.1:" + server.getPort() + "/api/tickets/reporter/13";
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("only view your own tickets"));
        } finally {
            server.stop();
        }
    }


    @Test
    void changedDatabaseRoleInvalidatesPreviouslyIssuedToken() throws Exception {
        AccessTokenService tokens = new AccessTokenService();
        String token = tokens.issue(new Student(12, "Student", "student@example.test", "", "S12", "CSE"));
        UserDAO changedRole = new UserDAO() {
            @Override public Optional<String> findRoleById(int userId) { return Optional.of("ADMIN"); }
        };
        ApiServer server = new ApiServer(0, tokens, changedRole, TokenRevocationStore.noOp());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "http://127.0.0.1:" + server.getPort() + "/api/tickets/reporter/12";
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("permissions changed"));
        } finally {
            server.stop();
        }
    }


    @Test
    void everyProtectedRouteRejectsAnonymousRequestsBeforeDatabaseAccess() throws Exception {
        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            String base = "http://127.0.0.1:" + server.getPort();
            for (String path : java.util.List.of(
                    "/api/tickets/1/history",
                    "/api/staff/tickets",
                    "/api/notifications/me",
                    "/api/emergencies",
                    "/api/resources",
                    "/api/bookings/mine",
                    "/api/admin/users",
                    "/api/admin/overview",
                    "/api/admin/reports/tickets.csv")) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(base + path))
                                .timeout(Duration.ofSeconds(3)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, response.statusCode(), "Anonymous GET must be rejected: " + path);
            }

            for (String path : java.util.List.of(
                    "/api/emergencies",
                    "/api/bookings",
                    "/api/notifications/1/read",
                    "/api/staff/tickets/1/status",
                    "/api/admin/users",
                    "/api/admin/tickets/1/assign")) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(base + path))
                                .timeout(Duration.ofSeconds(3))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, response.statusCode(), "Anonymous POST must be rejected: " + path);
            }
        } finally {
            server.stop();
        }
    }

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

            HttpResponse<String> readiness = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/health/ready"))
                            .timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if ("true".equalsIgnoreCase(System.getenv("CAMPUSOS_IT_ENABLED"))) {
                assertEquals(200, readiness.statusCode());
                assertTrue(readiness.body().contains("\"ready\""));
            } else {
                assertEquals(503, readiness.statusCode());
                assertTrue(readiness.body().contains("\"not_ready\""));
            }

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

            HttpResponse<String> history = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tickets/1/history")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, history.statusCode());

            HttpResponse<String> staff = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/staff/tickets")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, staff.statusCode());

            HttpResponse<String> notifications = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/notifications/me")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, notifications.statusCode());

            HttpResponse<String> resources = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/resources")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, resources.statusCode());

            HttpResponse<String> admin = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/users")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, admin.statusCode());
        } finally {
            server.stop();
        }
    }
    @Test
    void corsDoesNotReflectUntrustedOriginsAndSecurityHeadersArePresent() throws Exception {
        ApiServer server = new ApiServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            String url = "http://127.0.0.1:" + server.getPort() + "/api/health";

            HttpResponse<String> trusted = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Origin", "http://localhost:5173")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, trusted.statusCode());
            assertEquals("http://localhost:5173",
                    trusted.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
            assertEquals("nosniff", trusted.headers().firstValue("X-Content-Type-Options").orElse(""));
            assertEquals("no-store", trusted.headers().firstValue("Cache-Control").orElse(""));
            assertEquals("Origin", trusted.headers().firstValue("Vary").orElse(""));

            HttpResponse<String> untrusted = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Origin", "https://attacker.invalid")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, untrusted.statusCode());
            assertFalse(untrusted.headers().firstValue("Access-Control-Allow-Origin").isPresent(),
                    "The API must not reflect arbitrary origins into CORS headers");
        } finally {
            server.stop();
        }
    }



    @Test
    void gracefulShutdownAllowsAnInFlightAuthorizedCheckToFinish() throws Exception {
        CountDownLatch roleLookupEntered = new CountDownLatch(1);
        CountDownLatch releaseRoleLookup = new CountDownLatch(1);
        AccessTokenService tokens = new AccessTokenService();
        String token = tokens.issue(new Student(12, "Student", "student@example.test", "", "S12", "CSE"));
        UserDAO delayedUserDAO = new UserDAO() {
            @Override
            public Optional<String> findRoleById(int userId) {
                roleLookupEntered.countDown();
                try {
                    if (!releaseRoleLookup.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release role lookup");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
                return Optional.of("STUDENT");
            }
        };
        ApiServer server = new ApiServer(0, tokens, delayedUserDAO, TokenRevocationStore.noOp());
        server.start();
        boolean stopped = false;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.getPort() + "/api/admin/users"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            CompletableFuture<HttpResponse<String>> response =
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            assertTrue(roleLookupEntered.await(2, TimeUnit.SECONDS),
                    "Request should be inside the handler before shutdown begins");

            CompletableFuture<Void> release = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    releaseRoleLookup.countDown();
                }
            });
            server.stop();
            stopped = true;
            HttpResponse<String> completed = response.get(2, TimeUnit.SECONDS);
            release.get(2, TimeUnit.SECONDS);
            assertEquals(403, completed.statusCode(),
                    "The in-flight request should finish its authorization response during shutdown");
        } finally {
            releaseRoleLookup.countDown();
            if (!stopped) server.stop();
        }
    }

}
