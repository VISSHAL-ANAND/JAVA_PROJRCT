package security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {
    private static final int CLEANUP_THRESHOLD = 5_000;
    private static final int MAX_BUCKETS = 10_000;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private static final class Bucket {
        int attempts;
        Instant windowStart = Instant.now();
        Instant blockedUntil = Instant.MIN;
    }

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupAtMillis = new AtomicLong();

    public RateLimiter(int maxAttempts, Duration window, Duration blockDuration) {
        if (maxAttempts < 1 || window == null || window.isNegative() || window.isZero()
                || blockDuration == null || blockDuration.isNegative() || blockDuration.isZero()) {
            throw new IllegalArgumentException("Rate limiter settings must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
    }

    public boolean allow(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        cleanupIfNeeded(now);
        if (buckets.size() >= MAX_BUCKETS && !buckets.containsKey(normalized)) return false;

        Bucket bucket = buckets.computeIfAbsent(normalized, ignored -> new Bucket());
        synchronized (bucket) {
            if (now.isBefore(bucket.blockedUntil)) return false;
            if (Duration.between(bucket.windowStart, now).compareTo(window) >= 0) {
                bucket.attempts = 0;
                bucket.windowStart = now;
            }
            if (bucket.attempts >= maxAttempts) {
                bucket.blockedUntil = now.plus(blockDuration);
                return false;
            }
            bucket.attempts++;
            return true;
        }
    }

    public void reset(String key) {
        if (key != null) buckets.remove(key.trim().toLowerCase(Locale.ROOT));
    }

    private void cleanupIfNeeded(Instant now) {
        if (buckets.size() < CLEANUP_THRESHOLD) return;

        // The login hot path must not scan thousands of buckets on every request.
        long nowMillis = now.toEpochMilli();
        long scheduledAt = nextCleanupAtMillis.get();
        if (nowMillis < scheduledAt
                || !nextCleanupAtMillis.compareAndSet(scheduledAt, nowMillis + CLEANUP_INTERVAL_MILLIS)) {
            return;
        }

        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                boolean expiredWindow = Duration.between(bucket.windowStart, now)
                        .compareTo(window.plus(blockDuration)) >= 0;
                return expiredWindow && !now.isBefore(bucket.blockedUntil);
            }
        });
    }
}
