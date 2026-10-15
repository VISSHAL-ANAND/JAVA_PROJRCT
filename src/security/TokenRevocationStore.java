package security;

import java.sql.SQLException;

/** Persistent server-side access-token revocation store. */
public interface TokenRevocationStore {
    boolean isRevoked(String token) throws SQLException;

    void revoke(String token, long expiresAtEpochSeconds) throws SQLException;

    /** Test-only adapter for HTTP tests that deliberately avoid a database. */
    static TokenRevocationStore noOp() {
        return new TokenRevocationStore() {
            @Override public boolean isRevoked(String token) { return false; }
            @Override public void revoke(String token, long expiresAtEpochSeconds) { }
        };
    }
}
