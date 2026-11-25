-- Rookvale Admin Audit Log Schema (Append-Only)
-- Run in Supabase SQL Editor to enable server-side Admin Audit Log history.

create table if not exists public.admin_audit_logs (
    event_id text primary key,
    created_at timestamptz not null default now(),
    action_type text not null check (
        action_type in (
            'LEVEL_CHANGE',
            'GRANT_GEAR',
            'TELEPORT',
            'SPECTATOR_TOGGLE',
            'TIME_OF_DAY'
        )
    ),
    actor_name text not null check (char_length(actor_name) between 1 and 32),
    target_name text not null check (char_length(target_name) between 1 and 32),
    summary text not null check (char_length(summary) between 1 and 180),
    details jsonb not null default '{}'::jsonb
);

create index if not exists idx_admin_audit_logs_created_at
    on public.admin_audit_logs (created_at desc);

create index if not exists idx_admin_audit_logs_action_created_at
    on public.admin_audit_logs (action_type, created_at desc);

-- Append-Only Row-Level Security: allow INSERT and SELECT only (no UPDATE or DELETE policies)
alter table public.admin_audit_logs enable row level security;

drop policy if exists "admin_audit_logs_append_only_insert" on public.admin_audit_logs;
create policy "admin_audit_logs_append_only_insert"
    on public.admin_audit_logs
    for insert
    to anon, authenticated
    with check (actor_name = 'Admin');

drop policy if exists "admin_audit_logs_read_only_select" on public.admin_audit_logs;
create policy "admin_audit_logs_read_only_select"
    on public.admin_audit_logs
    for select
    to anon, authenticated
    using (true);

-- Explicitly revoke UPDATE and DELETE privileges from client roles to guarantee immutability
revoke update, delete, truncate on public.admin_audit_logs from anon, authenticated;
