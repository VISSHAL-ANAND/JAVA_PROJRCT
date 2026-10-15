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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_BOOKING_HTTP_SCALE_TEST_ENABLED", matches = "true")
class BookingHttpScaleTest {
    private static final int CONTENDERS = 100;

    @Test
    void concurrentHttpBookingRequestsNeverExceedCapacityOne() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emailPrefix = "booking-scale-" + suffix + "-";
        String passwordHash = PasswordHasher.hash("BookingScale-Test-Only-2026");
        UserDAO users = new UserDAO();
        AccessTokenService tokenService = new AccessTokenService();
        List<String> tokens = new ArrayList<>(CONTENDERS);
        int resourceId = 0;
        ApiServer server = null;

        try {
            for (int i = 0; i < CONTENDERS; i++) {
                String email = emailPrefix + i + "@example.test";
                int id = users.createUser("Booking Scale User " + i, email, passwordHash,
                        "STUDENT", "BSCALE" + suffix + i, "CSE", null);
                tokens.add(tokenService.issue(new Student(id, "Booking Scale User " + i, email,
                        "", "BSCALE" + suffix + i, "CSE")));
            }

            try (Connection admin = adminConnection();
                 PreparedStatement statement = admin.prepareStatement(
                         "INSERT INTO resources(name,type,quantity,available) VALUES(?,'ROOM',1,TRUE)",
                         java.sql.Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, "Booking Scale Room " + suffix);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    assertTrue(keys.next(), "The database must return the test resource ID");
                    resourceId = keys.getInt(1);
                }
            }

            server = new ApiServer(0);
            server.start();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getPort() + "/api/bookings");
            LocalDateTime start = LocalDateTime.now().plusDays(5).withSecond(0).withNano(0);
            LocalDateTime end = start.plusHours(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger conflicts = new AtomicInteger();
            List<String> unexpected = java.util.Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<Void>> pending = new ArrayList<>(CONTENDERS);
            long startedAt = System.nanoTime();

            for (int i = 0; i < CONTENDERS; i++) {
                String body = "{\"resourceId\":" + resourceId
                        + ",\"startTime\":\"" + start
                        + "\",\"endTime\":\"" + end + "\"}";
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + tokens.get(i))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                pending.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() == 201 && response.body().contains("\"bookingId\"")) {
                                successes.incrementAndGet();
                            } else if (response.statusCode() == 409) {
                                conflicts.incrementAndGet();
                            } else {
                                unexpected.add("HTTP " + response.statusCode() + ": " + response.body());
                            }
                        }));
            }
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                    .get(75, TimeUnit.SECONDS);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            System.out.printf("CAMPUSOS booking API contention: contenders=%d successes=%d conflicts=%d unexpected=%d totalMs=%d%n",
                    CONTENDERS, successes.get(), conflicts.get(), unexpected.size(), elapsed);

            assertTrue(unexpected.isEmpty(), "Unexpected booking responses: " + unexpected);
            assertEquals(1, successes.get(), "Capacity-one resource must admit exactly one booking");
            assertEquals(CONTENDERS - 1, conflicts.get(), "Every losing booking must receive HTTP 409");
            assertTrue(elapsed < 75_000, "Concurrent booking requests exceeded the CI time budget");
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM bookings WHERE resource_id = ? AND start_time = ? AND end_time = ?")) {
                statement.setInt(1, resourceId);
                statement.setTimestamp(2, java.sql.Timestamp.valueOf(start));
                statement.setTimestamp(3, java.sql.Timestamp.valueOf(end));
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1), "Database must contain exactly one accepted booking");
                }
            }
        } finally {
            if (server != null) server.stop();
            try (Connection admin = adminConnection()) {
                if (resourceId > 0) {
                    try (PreparedStatement statement = admin.prepareStatement("DELETE FROM bookings WHERE resource_id=?")) {
                        statement.setInt(1, resourceId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = admin.prepareStatement("DELETE FROM resources WHERE id=?")) {
                        statement.setInt(1, resourceId);
                        statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = admin.prepareStatement("DELETE FROM users WHERE email LIKE ?")) {
                    statement.setString(1, emailPrefix + "%@example.test");
                    statement.executeUpdate();
                }
            } finally {
                DBConnection.closePool();
            }
        }
    }

    private Connection adminConnection() throws SQLException {
        String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
        String username = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
        String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
        if (url == null || url.isBlank()) url = System.getenv("CAMPUSOS_DB_URL");
        if (username == null || username.isBlank()) username = System.getenv("CAMPUSOS_DB_USERNAME");
        if (password == null || password.isBlank()) password = System.getenv("CAMPUSOS_DB_PASSWORD");
        if (url == null || username == null || password == null)
            throw new SQLException("Admin database connection settings are missing");
        return DriverManager.getConnection(url, username, password);
    }
}
