package security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {
    @Test
    void blocksAfterConfiguredAttemptsAndCanReset() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertTrue(limiter.allow("student@example.test"));
        assertTrue(limiter.allow("student@example.test"));
        assertFalse(limiter.allow("student@example.test"));
        limiter.reset("STUDENT@example.test");
        assertTrue(limiter.allow("student@example.test"));
    }

    @Test
    void normalizesKeysAndRejectsInvalidConfiguration() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertTrue(limiter.allow(" Student@Example.test "));
        assertFalse(limiter.allow("student@example.test"));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimiter(0, Duration.ofMinutes(1), Duration.ofMinutes(1)));
    }
    @Test
    void throttlesExpensiveBucketCleanup() throws ReflectiveOperationException {
        RateLimiter limiter = new RateLimiter(10, Duration.ofMinutes(1), Duration.ofMinutes(1));
        for (int i = 0; i <= 5_000; i++) {
            assertTrue(limiter.allow("client-" + i));
        }

        Field field = RateLimiter.class.getDeclaredField("nextCleanupAtMillis");
        field.setAccessible(true);
        AtomicLong nextCleanupAtMillis = (AtomicLong) field.get(limiter);
        long scheduledCleanup = nextCleanupAtMillis.get();
        assertTrue(scheduledCleanup > System.currentTimeMillis(),
                "Crossing the cleanup threshold should schedule the next sweep");

        assertTrue(limiter.allow("follow-up-client"));
        assertEquals(scheduledCleanup, nextCleanupAtMillis.get(),
                "A request must not trigger another full bucket scan within the cleanup interval");
    }

}
