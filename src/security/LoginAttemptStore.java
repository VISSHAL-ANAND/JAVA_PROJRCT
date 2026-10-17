package security;

import java.sql.SQLException;

/** Shared login-attempt state used by all API instances connected to the same database. */
public interface LoginAttemptStore {
    /** Atomically records an attempt and returns false when the account is currently blocked. */
    boolean allowAttempt(String email) throws SQLException;

    /** Removes shared attempt history after a successful login. */
    void clear(String email) throws SQLException;

    /** Test adapter for API tests that intentionally do not connect to a database. */
    static LoginAttemptStore noOp() {
        return new LoginAttemptStore() {
            @Override public boolean allowAttempt(String email) { return true; }
            @Override public void clear(String email) { }
        };
    }
}
