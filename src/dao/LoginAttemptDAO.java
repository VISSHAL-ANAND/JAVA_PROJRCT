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
                return !result.next() || !result.getBoolean("is_blocked");
            }
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

    /** Records a failed authentication. Called only after the credential check fails. */
    public void recordFailure(String email) throws SQLException {
        if (email == null || email.isBlank()) return;
        String hash = fingerprint(email.trim().toLowerCase(Locale.ROOT));
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertIfMissing(connection, hash);
                int attempts;
                Timestamp windowStarted;
                Timestamp blockedUntil;
                Timestamp databaseNow;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT attempts, window_started_at, blocked_until, CURRENT_TIMESTAMP AS database_now "
                                + "FROM login_attempt_limits WHERE key_hash = ? FOR UPDATE")) {
                    select.setString(1, hash);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) throw new SQLException("Login attempt row disappeared during update");
                        attempts = result.getInt("attempts");
                        windowStarted = result.getTimestamp("window_started_at");
                        blockedUntil = result.getTimestamp("blocked_until");
                        databaseNow = result.getTimestamp("database_now");
                    }
                }

                if (blockedUntil != null && databaseNow.before(blockedUntil)) {
                    connection.commit();
                    return;
                }
                if (windowStarted == null
                        || Duration.between(windowStarted.toInstant(), databaseNow.toInstant()).compareTo(WINDOW) >= 0) {
                    attempts = 0;
                    windowStarted = databaseNow;
                }

                attempts++;
                Timestamp nextBlockedUntil = attempts >= MAX_ATTEMPTS
                        ? Timestamp.from(databaseNow.toInstant().plus(BLOCK_DURATION))
                        : null;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE login_attempt_limits SET attempts = ?, window_started_at = ?, "
                                + "blocked_until = ?, updated_at = CURRENT_TIMESTAMP WHERE key_hash = ?")) {
                    update.setInt(1, attempts);
                    update.setTimestamp(2, windowStarted);
                    update.setTimestamp(3, nextBlockedUntil);
                    update.setString(4, hash);
                    update.executeUpdate();
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

    private void insertIfMissing(Connection connection, String hash) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        String sql;
        if (product.contains("mysql")) {
            sql = "INSERT INTO login_attempt_limits "
                    + "(key_hash, attempts, window_started_at, blocked_until, updated_at) "
                    + "VALUES (?, 0, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE key_hash = VALUES(key_hash)";
        } else if (product.contains("postgresql")) {
            sql = "INSERT INTO login_attempt_limits "
                    + "(key_hash, attempts, window_started_at, blocked_until, updated_at) "
                    + "VALUES (?, 0, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP) "
                    + "ON CONFLICT (key_hash) DO NOTHING";
        } else {
            throw new SQLException("Shared login rate limiting supports MySQL and PostgreSQL only");
        }
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, hash);
            insert.executeUpdate();
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
