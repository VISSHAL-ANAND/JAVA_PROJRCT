package integration;

import dao.LoginAttemptDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CAMPUSOS_LOGIN_ATTEMPT_STORE_TEST_ENABLED", matches = "true")
class LoginAttemptStoreIntegrationTest {
    @Test
    void sharedStoresApplyAccountLockoutAndSuccessfulLoginCanClearIt() throws Exception {
        LoginAttemptDAO firstInstance = new LoginAttemptDAO();
        LoginAttemptDAO secondInstance = new LoginAttemptDAO();
        String email = "shared-login-" + UUID.randomUUID() + "@example.test";
        try {
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertTrue(firstInstance.allowAttempt(email), "The first five attempts should be admitted");
            }
            assertFalse(secondInstance.allowAttempt(email),
                    "A second API-instance store must observe the shared account lockout");
            secondInstance.clear(email);
            assertTrue(firstInstance.allowAttempt(email),
                    "Successful login cleanup should be visible to every API instance");
        } finally {
            secondInstance.clear(email);
        }
    }

    @Test
    void expiredLockoutCanRecoverEvenWhenRequestsArriveDuringTheBlock() throws Exception {
        LoginAttemptDAO store = new LoginAttemptDAO();
        String email = "expired-login-" + UUID.randomUUID() + "@example.test";
        String keyHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(email.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try {
            for (int attempt = 1; attempt <= 6; attempt++) {
                boolean allowed = store.allowAttempt(email);
                assertEquals(attempt <= 5, allowed);
            }
            try (Connection connection = database.DBConnection.getConnection()) {
                boolean mysql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
                String interval30 = mysql ? "INTERVAL 30 MINUTE" : "INTERVAL '30 minutes'";
                String interval1 = mysql ? "INTERVAL 1 MINUTE" : "INTERVAL '1 minute'";
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE login_attempt_limits SET attempts = 6, window_started_at = CURRENT_TIMESTAMP - "
                                + interval30 + ", blocked_until = CURRENT_TIMESTAMP + " + interval1
                                + ", updated_at = CURRENT_TIMESTAMP WHERE key_hash = ?")) {
                    update.setString(1, keyHash);
                    assertEquals(1, update.executeUpdate());
                }
            }

            assertFalse(store.allowAttempt(email), "The still-active lockout must reject requests");
            try (Connection connection = database.DBConnection.getConnection()) {
                boolean mysql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
                String interval1Second = mysql ? "INTERVAL 1 SECOND" : "INTERVAL '1 second'";
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE login_attempt_limits SET blocked_until = CURRENT_TIMESTAMP - "
                                + interval1Second + ", updated_at = CURRENT_TIMESTAMP WHERE key_hash = ?")) {
                    update.setString(1, keyHash);
                    assertEquals(1, update.executeUpdate());
                }
            }
            assertTrue(store.allowAttempt(email),
                    "Once the block expires, an old window must reset rather than re-blocking indefinitely");
        } finally {
            store.clear(email);
        }
    }

    @Test
    void concurrentFailuresAcrossStoreInstancesAllowOnlyFiveAttempts() throws Exception {
        LoginAttemptDAO firstInstance = new LoginAttemptDAO();
        LoginAttemptDAO secondInstance = new LoginAttemptDAO();
        String email = "parallel-login-" + UUID.randomUUID() + "@example.test";
        ExecutorService workers = Executors.newFixedThreadPool(10);
        try {
            AtomicInteger allowed = new AtomicInteger();
            List<Callable<Void>> attempts = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final LoginAttemptDAO store = i % 2 == 0 ? firstInstance : secondInstance;
                attempts.add(() -> {
                    if (store.allowAttempt(email)) allowed.incrementAndGet();
                    return null;
                });
            }
            List<Future<Void>> results = workers.invokeAll(attempts, 30, TimeUnit.SECONDS);
            for (Future<Void> result : results) {
                assertFalse(result.isCancelled(), "Concurrent login attempt exceeded timeout");
                result.get();
            }
            assertEquals(5, allowed.get(), "Only five concurrent failures should be admitted before lockout");
            assertFalse(firstInstance.allowAttempt(email));
        } finally {
            workers.shutdownNow();
            secondInstance.clear(email);
        }
    }
}
