-- Apply once to an existing CAMPUSOS MySQL 8 database.
-- Fresh databases created with resources/campusos.sql already include this table.
USE campusos;

CREATE TABLE IF NOT EXISTS token_revocations (
    token_hash CHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_token_revocations_expiry (expires_at)
);
