package dao;

import database.DBConnection;
import security.TokenRevocationStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HexFormat;

/** Stores only SHA-256 token fingerprints, never bearer-token plaintext. */

public final class TokenRevocationDAO implements TokenRevocationStore {
    private static final long CLEANUP_INTERVAL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final AtomicLong NEXT_CLEANUP_AT_MILLIS = new AtomicLong();
    @Override
    public boolean isRevoked(String token) throws SQLException {
        if (token == null || token.isBlank()) return false;
        String sql = "SELECT 1 FROM token_revocations WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fingerprint(token));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public void revoke(String token, long expiresAtEpochSeconds) throws SQLException {
        if (token == null || token.isBlank() || expiresAtEpochSeconds <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("A valid unexpired token is required");
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Expired rows are housekeeping, not part of the logout's correctness path.
                // Running the same range DELETE in every concurrent logout caused MySQL
                // deadlocks under a 1,000-token burst. At most one thread per process cleans
                // expired rows every five minutes; the revocation INSERT still runs every time.
                if (shouldCleanupExpiredRows()) {
                    try (PreparedStatement cleanup = connection.prepareStatement(
                            "DELETE FROM token_revocations WHERE expires_at <= CURRENT_TIMESTAMP")) {
                        cleanup.executeUpdate();
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO token_revocations(token_hash, expires_at) VALUES(?, ?)")) {
                    insert.setString(1, fingerprint(token));
                    insert.setTimestamp(2, Timestamp.from(Instant.ofEpochSecond(expiresAtEpochSeconds)));
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


    private boolean shouldCleanupExpiredRows() {
        long now = System.currentTimeMillis();
        while (true) {
            long scheduled = NEXT_CLEANUP_AT_MILLIS.get();
            if (now < scheduled) return false;
            if (NEXT_CLEANUP_AT_MILLIS.compareAndSet(scheduled, now + CLEANUP_INTERVAL_MILLIS)) {
                return true;
            }
        }
    }

    private String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint access token", e);
        }
    }
}
