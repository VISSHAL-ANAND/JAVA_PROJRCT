package security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private PasswordHasher() {}
    public static String hash(String password) {
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derive(password.toCharArray(), salt, ITERATIONS));
    }
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.startsWith("pbkdf2$")) return false;
        try {
            String[] p = stored.split("\$");
            byte[] actual = derive(password.toCharArray(), Base64.getDecoder().decode(p[2]), Integer.parseInt(p[1]));
            return MessageDigest.isEqual(actual, Base64.getDecoder().decode(p[3]));
        } catch (RuntimeException e) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) { throw new IllegalStateException("Password hashing unavailable", e); }
    }
}
