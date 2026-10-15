package security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class PasswordHasher {
    private static final int ITERATIONS = 120_000;
    private static final int MIN_VERIFY_ITERATIONS = 10_000;
    private static final int MAX_VERIFY_ITERATIONS = 1_000_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH_BYTES = KEY_LENGTH / 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ThreadLocal<SecretKeyFactory> FACTORIES = ThreadLocal.withInitial(() -> {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    });

    private PasswordHasher() {}

    public static String hash(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        char[] characters = password.toCharArray();
        byte[] derived = null;
        try {
            derived = derive(characters, salt, ITERATIONS);
            return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derived);
        } finally {
            Arrays.fill(characters, '\0');
            if (derived != null) Arrays.fill(derived, (byte) 0);
        }
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.startsWith("pbkdf2$")) return false;
        char[] characters = null;
        byte[] actual = null;
        byte[] expected = null;
        byte[] salt = null;
        try {
            String[] parts = stored.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            // Bound attacker-controlled/corrupted stored values to avoid turning a login
            // attempt into an unbounded CPU workload. Current hashes use 120,000 rounds.
            if (iterations < MIN_VERIFY_ITERATIONS || iterations > MAX_VERIFY_ITERATIONS) return false;
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length < SALT_LENGTH || salt.length > 64 || expected.length != HASH_LENGTH_BYTES) {
                return false;
            }
            characters = password.toCharArray();
            actual = derive(characters, salt, iterations);
            return MessageDigest.isEqual(actual, expected);
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (characters != null) Arrays.fill(characters, '\0');
            if (actual != null) Arrays.fill(actual, (byte) 0);
            if (expected != null) Arrays.fill(expected, (byte) 0);
            if (salt != null) Arrays.fill(salt, (byte) 0);
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
        try {
            return FACTORIES.get().generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
