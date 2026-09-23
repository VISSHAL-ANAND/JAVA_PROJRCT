package security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private static final class Bucket {
        int attempts;
        Instant windowStart = Instant.now();
        Instant blockedUntil = Instant.MIN;
    }

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, Duration window, Duration blockDuration) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
    }

    public boolean allow(String key) {
        if (key == null || key.isBlank()) return false;
        Bucket bucket = buckets.computeIfAbsent(key.toLowerCase(), k -> new Bucket());

        synchronized (bucket) {
            Instant now = Instant.now();

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
        if (key != null) buckets.remove(key.toLowerCase());
    }
}
