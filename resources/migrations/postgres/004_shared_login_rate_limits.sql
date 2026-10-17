-- Apply once to an existing CAMPUSOS PostgreSQL/Supabase database.
-- Fresh databases created with resources/supabase_schema.sql already include this table.
CREATE TABLE IF NOT EXISTS public.login_attempt_limits (
    key_hash char(64) primary key,
    attempts integer not null default 0,
    window_started_at timestamptz not null,
    blocked_until timestamptz,
    updated_at timestamptz not null
);
CREATE INDEX IF NOT EXISTS idx_login_attempt_limits_updated
    ON public.login_attempt_limits(updated_at);

-- Grant only required operations if the least-privilege app role already exists.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'campusos_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.login_attempt_limits TO campusos_app';
    END IF;
END
$$;
