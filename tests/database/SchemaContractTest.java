package database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void mysqlAndPostgresSchemasIncludePersistentTokenRevocationStore() throws IOException {
        String mysql = resource("campusos.sql").toLowerCase(java.util.Locale.ROOT);
        String postgres = resource("supabase_schema.sql").toLowerCase(java.util.Locale.ROOT);
        assertTrue(mysql.contains("create table if not exists token_revocations"));
        assertTrue(mysql.contains("token_hash char(64) primary key"));
        assertTrue(postgres.contains("create table if not exists public.token_revocations"));
        assertTrue(postgres.contains("token_hash char(64) primary key"));
        assertTrue(resource("migrations/mysql/003_token_revocations.sql")
                .toLowerCase(java.util.Locale.ROOT).contains("token_revocations"));
        assertTrue(resource("migrations/postgres/002_token_revocations.sql")
                .toLowerCase(java.util.Locale.ROOT).contains("token_revocations"));
        assertTrue(mysql.contains("create table if not exists login_attempt_limits"));
        assertTrue(postgres.contains("create table if not exists public.login_attempt_limits"));
        assertTrue(resource("migrations/mysql/005_shared_login_rate_limits.sql")
                .toLowerCase(java.util.Locale.ROOT).contains("login_attempt_limits"));
        assertTrue(resource("migrations/postgres/004_shared_login_rate_limits.sql")
                .toLowerCase(java.util.Locale.ROOT).contains("login_attempt_limits"));
    }

    @Test
    void largeCampusDirectoryAndRecentTicketQueriesHaveSupportingIndexes() throws IOException {
        String mysql = resource("campusos.sql").toLowerCase(java.util.Locale.ROOT);
        String postgres = resource("supabase_schema.sql").toLowerCase(java.util.Locale.ROOT);
        assertTrue(mysql.contains("create index idx_users_role_name_id on users(role, name, id)"));
        assertTrue(mysql.contains("create index idx_users_role_available_name_id on users(role, available, name, id)"));
        assertTrue(mysql.contains("create index idx_tickets_created_id on tickets(created_at desc, id desc)"));
        assertTrue(postgres.contains("idx_users_role_name_id on public.users(role, name, id)"));
        assertTrue(postgres.contains("idx_users_role_available_name_id on public.users(role, available, name, id)"));
        assertTrue(postgres.contains("idx_tickets_created_id on public.tickets(created_at desc, id desc)"));
        assertTrue(resource("migrations/mysql/004_large_campus_query_indexes.sql")
                .toLowerCase(java.util.Locale.ROOT).contains("idx_tickets_created_id"));
        assertTrue(resource("migrations/postgres/003_large_campus_query_indexes.sql")
                .toLowerCase(java.util.Locale.ROOT).contains("idx_tickets_created_id"));
    }

    @Test
    void genericPostgresHostingSchemaAndAppRoleDoNotRequireSupabaseRoles() throws IOException {
        String schema = resource("postgres_schema.sql").toLowerCase(java.util.Locale.ROOT);
        String appRole = resource("postgres_app_role.sql.example").toLowerCase(java.util.Locale.ROOT);

        assertTrue(schema.contains("create table if not exists public.users"));
        assertTrue(schema.contains("create table if not exists public.token_revocations"));
        assertTrue(schema.contains("create table if not exists public.login_attempt_limits"));
        assertFalse(schema.contains("from anon, authenticated"),
                "Generic PostgreSQL bootstrap must not depend on Supabase-only roles");
        assertTrue(appRole.contains("grant connect on database campusos to campusos_app"));
        assertTrue(appRole.contains("grant select, insert, delete on table public.token_revocations"));
        assertTrue(appRole.contains("grant select, insert, update, delete on table public.login_attempt_limits"));
        assertTrue(appRole.contains("grant update (available) on table public.users"));
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing classpath resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
