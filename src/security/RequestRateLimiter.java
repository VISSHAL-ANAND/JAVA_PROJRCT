package security;
import java.time.Duration;
public class RequestRateLimiter extends RateLimiter {
    public RequestRateLimiter() { super(60, Duration.ofMinutes(1), Duration.ofMinutes(1)); }
}
