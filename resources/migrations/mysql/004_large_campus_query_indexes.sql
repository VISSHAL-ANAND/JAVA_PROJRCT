-- Apply once to an existing CAMPUSOS MySQL 8 database.
-- Fresh databases created from resources/campusos.sql already include these indexes.
USE campusos;

DROP PROCEDURE IF EXISTS campusos_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE campusos_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @campusos_index_sql = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` (', p_columns, ')');
        PREPARE campusos_index_stmt FROM @campusos_index_sql;
        EXECUTE campusos_index_stmt;
        DEALLOCATE PREPARE campusos_index_stmt;
    END IF;
END$$
DELIMITER ;

CALL campusos_add_index_if_missing('users', 'idx_users_role_name_id', 'role, name, id');
CALL campusos_add_index_if_missing('users', 'idx_users_role_available_name_id', 'role, available, name, id');
CALL campusos_add_index_if_missing('tickets', 'idx_tickets_created_id', 'created_at DESC, id DESC');

DROP PROCEDURE campusos_add_index_if_missing;

-- The new composite index retains role as its leading column, so the old
-- single-column role index is redundant after the replacement is in place.
DROP PROCEDURE IF EXISTS campusos_drop_index_if_exists;
DELIMITER $$
CREATE PROCEDURE campusos_drop_index_if_exists(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @campusos_drop_sql = CONCAT('DROP INDEX `', p_index, '` ON `', p_table, '`');
        PREPARE campusos_drop_stmt FROM @campusos_drop_sql;
        EXECUTE campusos_drop_stmt;
        DEALLOCATE PREPARE campusos_drop_stmt;
    END IF;
END$$
DELIMITER ;

CALL campusos_drop_index_if_exists('users', 'idx_users_role');
DROP PROCEDURE campusos_drop_index_if_exists;
