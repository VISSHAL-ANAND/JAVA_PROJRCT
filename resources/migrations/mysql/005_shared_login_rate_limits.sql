-- Apply once to an existing CAMPUSOS MySQL 8 database.
-- Fresh databases created with resources/campusos.sql already include this table.
USE campusos;

CREATE TABLE IF NOT EXISTS login_attempt_limits (
    key_hash CHAR(64) PRIMARY KEY,
    attempts INT NOT NULL DEFAULT 0,
    window_started_at TIMESTAMP NOT NULL,
    blocked_until TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_login_attempt_limits_updated (updated_at)
);
