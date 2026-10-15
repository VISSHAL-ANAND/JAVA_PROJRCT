-- Apply once to an existing CAMPUSOS PostgreSQL/Supabase database.
-- Fresh databases created from resources/supabase_schema.sql already include these indexes.
create index if not exists idx_users_role_name_id
    on public.users(role, name, id);
create index if not exists idx_users_role_available_name_id
    on public.users(role, available, name, id);
create index if not exists idx_tickets_created_id
    on public.tickets(created_at desc, id desc);

-- The composite index above preserves the role-leading lookup and replaces this
-- redundant single-column index on existing installations.
drop index if exists public.idx_users_role;
