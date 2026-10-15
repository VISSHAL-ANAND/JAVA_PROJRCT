package database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractTest {
    @Test
    void mysqlAndPostgresSchemasEnforceResourceAndBookingInvariants() throws IOException {
        String mysql = resource("campusos.sql").toLowerCase(java.util.Locale.ROOT);
        String postgres = resource("supabase_schema.sql").toLowerCase(java.util.Locale.ROOT);

        assertTrue(mysql.contains("check (quantity >= 0)"),
                "MySQL bootstrap schema must reject negative resource quantities");
        assertTrue(mysql.contains("check (start_time < end_time)"),
                "MySQL bootstrap schema must reject empty or reversed booking intervals");
        assertTrue(postgres.contains("check (quantity >= 0)"),
                "PostgreSQL schema must reject negative resource quantities");
        assertTrue(postgres.contains("check (start_time < end_time)"),
                "PostgreSQL schema must reject empty or reversed booking intervals");
    }

    @Test
    void existingMysqlMigrationAddsTheSameIntegrityConstraints() throws IOException {
        String migration = resource("migrations/mysql/002_booking_resource_integrity_checks.sql")
                .toLowerCase(java.util.Locale.ROOT);

        assertTrue(migration.contains("chk_resources_quantity_nonnegative"));
        assertTrue(migration.contains("check (quantity >= 0)"));
        assertTrue(migration.contains("chk_bookings_time_order"));
        assertTrue(migration.contains("check (start_time < end_time)"));
        assertTrue(migration.contains("select count(*) from bookings where start_time >= end_time"),
                "Migration must tell operators how to identify invalid existing bookings before applying checks");
        assertTrue(migration.contains("select count(*) from resources where quantity < 0"),
                "Migration must tell operators how to identify negative existing quantities before applying checks");
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing classpath resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
