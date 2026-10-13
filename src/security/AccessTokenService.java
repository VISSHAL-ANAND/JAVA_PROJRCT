package security;

import model.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

public final class AccessTokenService {
    private static final long TOKEN_LIFETIME_SECONDS = 2 * 60 * 60;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final byte[] secret;

    public AccessTokenService() {
        this.secret = resolveSecret(
                System.getenv("CAMPUSOS_AUTH_SECRET"),
                "production".equalsIgnoreCase(System.getenv("CAMPUSOS_ENV"))
        );
    }

    static byte[] resolveSecret(String configured, boolean production) {
        if (configured != null && !configured.isBlank()) {
            if (configured.length() < 32) {
                throw new IllegalStateException("CAMPUSOS_AUTH_SECRET must contain at least 32 characters");
            }
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        if (production) {
            throw new IllegalStateException(
                    "CAMPUSOS_AUTH_SECRET must be configured with at least 32 characters in production"
            );
        }

        // Local development only; tokens become invalid when this process restarts.
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        return generated;
    }

    public String issue(User user) {
        if (user == null || user.getId() <= 0 || user.getRole() == null) {
            throw new IllegalArgumentException("A valid user is required");
        }
        long expiresAt = Instant.now().getEpochSecond() + TOKEN_LIFETIME_SECONDS;
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        String payload = user.getId() + "|" + user.getRole() + "|" + expiresAt + "|" + ENCODER.encodeToString(nonce);
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + ENCODER.encodeToString(sign(encodedPayload));
    }

    public Principal verify(String token) {
        if (token == null || token.isBlank() || token.length() > 2048) return null;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) return null;
        try {
            byte[] suppliedSignature = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), suppliedSignature)) return null;
            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String[] claims = payload.split("\\|", -1);
            if (claims.length != 4) return null;
            int userId = Integer.parseInt(claims[0]);
            long expiresAt = Long.parseLong(claims[2]);
            if (userId <= 0 || expiresAt <= Instant.now().getEpochSecond()) return null;
            if (claims[1].isBlank() || claims[3].isBlank()) return null;
            return new Principal(userId, claims[1], expiresAt);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign access token", e);
        }
    }

    public record Principal(int userId, String role, long expiresAt) {}
}
