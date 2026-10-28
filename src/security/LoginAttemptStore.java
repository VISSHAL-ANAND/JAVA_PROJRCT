package security;

import java.sql.SQLException;

/** Shared login-attempt state used by all API instances connected to the same database. */
public interface LoginAttemptStore {
    /** Checks the shared lockout state without counting a successful login as a failure. */
    boolean allowAttempt(String email) throws SQLException;

    /** Atomically records a failed credential attempt and applies the shared lockout policy. */
    void recordFailure(String email) throws SQLException;

    /** Removes shared failure history after a successful login. */
    void clear(String email) throws SQLException;

    /** Test adapter for API tests that intentionally do not connect to a database. */
    static LoginAttemptStore noOp() {
        return new LoginAttemptStore() {
            @Override public boolean allowAttempt(String email) { return true; }
            @Override public void recordFailure(String email) { }
            @Override public void clear(String email) { }
        };
    }
}
