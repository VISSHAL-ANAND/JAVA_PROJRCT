package dao;

import database.DBConnection;
import security.LoginAttemptStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database-backed login attempt limiter shared across API instances.
 * Each account is keyed by a one-way SHA-256 fingerprint so email addresses are not stored
 * in this table. Row locks make attempt updates atomic across concurrent requests/instances.
 */
public final class LoginAttemptDAO implements LoginAttemptStore {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final long CLEANUP_INTERVAL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final AtomicLong NEXT_CLEANUP_AT_MILLIS = new AtomicLong();

    @Override
    public boolean allowAttempt(String email) throws SQLException {
        if (email == null || email.isBlank()) return true;
        String hash = fingerprint(email.trim().toLowerCase(Locale.ROOT));
        cleanupIfDue();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT blocked_until > CURRENT_TIMESTAMP AS is_blocked "
                             + "FROM login_attempt_limits WHERE key_hash = ?")) {
            statement.setString(1, hash);
            try (ResultSet result = statement.executeQuery()) {
                // No row means no recorded failed credentials for this account.
                return !result.next() || !result.getBoolean("is_blocked");
            }
        }
    }

    @Override
    public void recordFailure(String email) throws SQLException {
        if (email == null || email.isBlank()) return;
        String hash = fingerprint(email.trim().toLowerCase(Locale.ROOT));
        cleanupIfDue();
        try (Connection connection = DBConnection.getConnection()) {
            // A single atomic upsert serializes failures for this account across API replicas.
            recordAttemptAtomically(connection, hash);
        }
    }

    @Override
    public void clear(String email) throws SQLException {
        if (email == null || email.isBlank()) return;
        String hash = fingerprint(email.trim().toLowerCase(Locale.ROOT));
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM login_attempt_limits WHERE key_hash = ?")) {
            statement.setString(1, hash);
            statement.executeUpdate();
        }
    }

    /** Performs an atomic upsert so concurrent API instances cannot all pass the same limit. */
    private void recordAttemptAtomically(Connection connection, String hash) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        String sql;
        if (product.contains("mysql")) {
            // MySQL evaluates duplicate-key assignments left-to-right; the blocked check uses
            // the incremented attempts value, blocking the sixth attempt after five are admitted.
            sql = "INSERT INTO login_attempt_limits "
                    + "(key_hash, attempts, window_started_at, blocked_until, updated_at) "
                    + "VALUES (?, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "attempts = CASE WHEN blocked_until > CURRENT_TIMESTAMP THEN attempts "
                    + "WHEN window_started_at <= CURRENT_TIMESTAMP - INTERVAL " + WINDOW.toMinutes() + " MINUTE THEN 1 "
                    + "ELSE attempts + 1 END, "
                    + "window_started_at = CASE WHEN blocked_until > CURRENT_TIMESTAMP THEN window_started_at "
                    + "WHEN window_started_at <= CURRENT_TIMESTAMP - INTERVAL " + WINDOW.toMinutes() + " MINUTE "
                    + "THEN CURRENT_TIMESTAMP ELSE window_started_at END, "
                    + "blocked_until = CASE WHEN blocked_until > CURRENT_TIMESTAMP THEN blocked_until "
                    + "WHEN window_started_at > CURRENT_TIMESTAMP - INTERVAL " + WINDOW.toMinutes() + " MINUTE "
                    + "AND attempts >= " + MAX_ATTEMPTS + " THEN CURRENT_TIMESTAMP + INTERVAL "
                    + BLOCK_DURATION.toMinutes() + " MINUTE ELSE NULL END, "
                    + "updated_at = CURRENT_TIMESTAMP";
        } else if (product.contains("postgresql")) {
            sql = "INSERT INTO login_attempt_limits "
                    + "(key_hash, attempts, window_started_at, blocked_until, updated_at) "
                    + "VALUES (?, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP) "
                    + "ON CONFLICT (key_hash) DO UPDATE SET "
                    + "attempts = CASE WHEN login_attempt_limits.blocked_until > CURRENT_TIMESTAMP "
                    + "THEN login_attempt_limits.attempts "
                    + "WHEN login_attempt_limits.window_started_at <= CURRENT_TIMESTAMP - INTERVAL '" + WINDOW.toMinutes() + " minutes' THEN 1 "
                    + "ELSE login_attempt_limits.attempts + 1 END, "
                    + "window_started_at = CASE WHEN login_attempt_limits.blocked_until > CURRENT_TIMESTAMP "
                    + "THEN login_attempt_limits.window_started_at "
                    + "WHEN login_attempt_limits.window_started_at <= CURRENT_TIMESTAMP - INTERVAL '" + WINDOW.toMinutes() + " minutes' "
                    + "THEN CURRENT_TIMESTAMP ELSE login_attempt_limits.window_started_at END, "
                    + "blocked_until = CASE WHEN login_attempt_limits.blocked_until > CURRENT_TIMESTAMP "
                    + "THEN login_attempt_limits.blocked_until "
                    + "WHEN login_attempt_limits.window_started_at > CURRENT_TIMESTAMP - INTERVAL '" + WINDOW.toMinutes() + " minutes' "
                    + "AND login_attempt_limits.attempts >= " + (MAX_ATTEMPTS - 1) + " THEN CURRENT_TIMESTAMP + INTERVAL '"
                    + BLOCK_DURATION.toMinutes() + " minutes' ELSE NULL END, updated_at = CURRENT_TIMESTAMP";
        } else {
            throw new SQLException("Shared login rate limiting supports MySQL and PostgreSQL only");
        }
        try (PreparedStatement upsert = connection.prepareStatement(sql)) {
            upsert.setString(1, hash);
            upsert.executeUpdate();
        }
    }

    private void cleanupIfDue() {
        long now = System.currentTimeMillis();
        while (true) {
            long scheduled = NEXT_CLEANUP_AT_MILLIS.get();
            if (now < scheduled) return;
            if (NEXT_CLEANUP_AT_MILLIS.compareAndSet(scheduled, now + CLEANUP_INTERVAL_MILLIS)) break;
        }
        Timestamp cutoff = Timestamp.from(Instant.now().minus(RETENTION));
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement cleanup = connection.prepareStatement(
                     "DELETE FROM login_attempt_limits WHERE updated_at < ?")) {
            cleanup.setTimestamp(1, cutoff);
            cleanup.executeUpdate();
        } catch (SQLException ignored) {
            // Housekeeping must never turn a healthy authentication attempt into an outage.
            // The next process-local cleanup window will retry.
        }
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint login key", e);
        }
    }
}
