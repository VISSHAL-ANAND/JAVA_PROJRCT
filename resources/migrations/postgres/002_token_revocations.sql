-- Apply once to an existing CAMPUSOS PostgreSQL/Supabase database.
-- Fresh databases created with resources/supabase_schema.sql already include this table.
CREATE TABLE IF NOT EXISTS public.token_revocations (
    token_hash CHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_token_revocations_expiry
    ON public.token_revocations(expires_at);

-- Add only the required privileges when the least-privilege app role already exists.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'campusos_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, DELETE ON TABLE public.token_revocations TO campusos_app';
    END IF;
END
$$;
