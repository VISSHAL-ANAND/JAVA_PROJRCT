-- Apply once to an existing CAMPUSOS MySQL 8 database.
-- For a fresh database, resources/campusos.sql already includes these constraints.
-- Inspect and repair invalid rows before running this migration; do not delete user data blindly.
USE campusos;

-- These queries should both return zero before applying the constraints:
-- SELECT COUNT(*) FROM resources WHERE quantity < 0;
-- SELECT COUNT(*) FROM bookings WHERE start_time >= end_time;

ALTER TABLE resources
    ADD CONSTRAINT chk_resources_quantity_nonnegative CHECK (quantity >= 0);

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_time_order CHECK (start_time < end_time);
