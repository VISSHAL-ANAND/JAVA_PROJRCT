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
    private static final int EVICTION_SAMPLE_SIZE = 32;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private static final class Bucket {
        int attempts;
        Instant windowStart = Instant.now();
        Instant blockedUntil = Instant.MIN;
        long lastAccessMillis = System.currentTimeMillis();
    }

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Object bucketCreationLock = new Object();
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

        Bucket bucket = buckets.get(normalized);
        if (bucket == null) {
            synchronized (bucketCreationLock) {
                bucket = buckets.get(normalized);
                if (bucket == null) {
                    if (buckets.size() >= MAX_BUCKETS) {
                        evictLeastRecentlyUsedBucket(now);
                    }
                    bucket = new Bucket();
                    buckets.put(normalized, bucket);
                }
            }
        }
        synchronized (bucket) {
            bucket.lastAccessMillis = now.toEpochMilli();
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

    /**
     * Keep memory bounded without turning high-cardinality login traffic into a global
     * lockout for every new key. Inspect only a bounded sample so each new key does not
     * trigger a full 10,000-entry scan. Prefer an unblocked least-recently-used bucket from
     * that sample; if all sampled buckets are blocked, evict the oldest sampled bucket.
     * This is a bounded, per-process limiter, not a substitute for a shared distributed limiter.
     */
    private void evictLeastRecentlyUsedBucket(Instant now) {
        String candidateKey = null;
        long oldestAccess = Long.MAX_VALUE;
        boolean candidateIsActive = true;
        int inspected = 0;
        for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                boolean active = now.isBefore(bucket.blockedUntil);
                long lastAccess = bucket.lastAccessMillis;
                if (candidateKey == null
                        || (candidateIsActive && !active)
                        || (candidateIsActive == active && lastAccess < oldestAccess)) {
                    candidateKey = entry.getKey();
                    oldestAccess = lastAccess;
                    candidateIsActive = active;
                }
            }
            if (++inspected >= EVICTION_SAMPLE_SIZE) break;
        }
        if (candidateKey != null) buckets.remove(candidateKey);
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
