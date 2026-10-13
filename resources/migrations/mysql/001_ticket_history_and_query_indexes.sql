-- Apply once to an existing CAMPUSOS MySQL 8 database.
-- For a fresh database, use resources/campusos.sql instead.
USE campusos;

CREATE TABLE IF NOT EXISTS ticket_history (
    id INT PRIMARY KEY AUTO_INCREMENT,
    ticket_id INT NOT NULL,
    changed_by INT NULL,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by) REFERENCES users(id)
);
ALTER TABLE ticket_history MODIFY changed_by INT NULL;

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

CALL campusos_add_index_if_missing('ticket_history', 'idx_ticket_history_ticket_created', 'ticket_id, created_at');
CALL campusos_add_index_if_missing('tickets', 'idx_tickets_reported_by_created', 'reported_by, created_at');
CALL campusos_add_index_if_missing('tickets', 'idx_tickets_assignee_status', 'assigned_technician_id, status');
CALL campusos_add_index_if_missing('bookings', 'idx_bookings_resource_start', 'resource_id, start_time');
CALL campusos_add_index_if_missing('bookings', 'idx_bookings_user_start', 'booked_by, start_time');
CALL campusos_add_index_if_missing('emergencies', 'idx_emergencies_created', 'created_at');

DROP PROCEDURE campusos_add_index_if_missing;
