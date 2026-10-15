package integration;

import dao.TokenRevocationDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the persistent logout-revocation table with a thousand simultaneous tokens.
 * Enabled only against disposable CI databases; it never touches a hosted environment.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUSOS_TOKEN_REVOCATION_LOAD_TEST_ENABLED", matches = "true")
class TokenRevocationLoadTest {
    private static final int TOKEN_COUNT = 1_000;
    private static final int WORKERS = 32;

    @Test
    void persistsAndChecksOneThousandConcurrentRevocations() throws Exception {
        TokenRevocationDAO store = new TokenRevocationDAO();
        long expiresAt = Instant.now().plusSeconds(7_200).getEpochSecond();
        ExecutorService workers = Executors.newFixedThreadPool(WORKERS);
        try {
            List<Callable<Void>> revokeTasks = new ArrayList<>(TOKEN_COUNT);
            for (int i = 0; i < TOKEN_COUNT; i++) {
                String token = "load-test-revocation-" + i + "-" + java.util.UUID.randomUUID();
                revokeTasks.add(() -> {
                    store.revoke(token, expiresAt);
                    if (!store.isRevoked(token)) {
                        throw new AssertionError("Revoked token was not found");
                    }
                    return null;
                });
            }

            long startedAt = System.nanoTime();
            List<Future<Void>> results = workers.invokeAll(revokeTasks, 45, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            for (Future<Void> result : results) {
                assertTrue(!result.isCancelled(), "Revocation load task exceeded 45 seconds");
                result.get();
            }

            System.out.printf("CAMPUSOS token revocation load: tokens=%d workers=%d totalMs=%d%n",
                    TOKEN_COUNT, WORKERS, elapsedMillis);
            assertTrue(elapsedMillis < 30_000,
                    "1,000 concurrent token revocations and verification exceeded 30 seconds");
        } finally {
            workers.shutdownNow();
        }
    }
}
