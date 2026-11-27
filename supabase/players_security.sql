-- Rookvale: lock down public.players and the admin audit log.
--
-- Run this ONCE in the Supabase SQL Editor (it is safe to re-run).
-- Ship together with the app update from the "server-side PIN checks" PR: older builds write
-- straight to the table and will stop cloud-saving after this runs (they keep saving locally),
-- and the new build can't cloud-save until this has been run.
--
-- What it does:
--   * The app can no longer INSERT/UPDATE/DELETE public.players directly, and can no longer
--     read the pin_hash column. Public roster columns stay readable.
--   * All logins and saves go through rv_load_player / rv_save_player, which verify the PIN
--     server-side and lock a character for 15 minutes after 5 wrong PINs.
--   * Stored PIN hashes are re-hashed with bcrypt so a leak of the column is not crackable offline.
--   * Admin status lives in players.is_admin and is set only by the server.
--   * The old "Admin" row (whose passcode was public) is locked. See "Claiming Admin" at the bottom.
--   * admin_audit_logs can only be written by a verified admin via rv_append_admin_audit.

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------
-- Private support tables (no client access at all)
-- ---------------------------------------------------------------------------
create table if not exists public.rv_config (
    key   text primary key,
    value text not null
);

create table if not exists public.rv_auth_attempts (
    attempt_key    text primary key,
    failures       int not null default 0,
    window_started timestamptz not null default now(),
    locked_until   timestamptz
);

alter table public.rv_config enable row level security;
alter table public.rv_auth_attempts enable row level security;
revoke all on public.rv_config, public.rv_auth_attempts from anon, authenticated;

-- ---------------------------------------------------------------------------
-- players: new column + one-time data migration
-- ---------------------------------------------------------------------------
alter table public.players add column if not exists is_admin boolean not null default false;
alter table public.players alter column pin_hash drop not null;

do $$
begin
    if not exists (select 1 from public.rv_config where key = 'players_security_v1') then
        -- Re-hash the client's PBKDF2 hex with bcrypt (existing PINs keep working).
        update public.players
           set pin_hash = extensions.crypt(pin_hash, extensions.gen_salt('bf', 8))
         where pin_hash is not null
           and pin_hash !~ '^\$2[abxy]\$';

        -- Nobody is admin until claimed; the old Admin passcode was public, so lock that row.
        update public.players set is_admin = false;
        update public.players set pin_hash = null
         where lower(trim(character_name)) = 'admin';

        insert into public.rv_config (key, value) values ('players_security_v1', now()::text);
    end if;
end $$;

-- Case-insensitive unique names (skipped with a notice if duplicates already exist).
do $$
begin
    create unique index if not exists players_character_name_ci_key
        on public.players (lower(trim(character_name)));
exception when unique_violation then
    raise notice 'Duplicate character names exist; unique index not created. Resolve duplicates and re-run.';
end $$;

-- ---------------------------------------------------------------------------
-- players: privileges. Read-only public roster, no writes, pin_hash hidden.
-- ---------------------------------------------------------------------------
alter table public.players enable row level security;

revoke all on public.players from anon, authenticated;
grant select (player_id, character_name, save_data, updated_at, is_admin)
    on public.players to anon, authenticated;

drop policy if exists "rv_players_public_read" on public.players;
create policy "rv_players_public_read"
    on public.players for select
    to anon, authenticated
    using (true);

-- ---------------------------------------------------------------------------
-- Internal: PIN check with lockout. Returns 'ok' | 'bad' | 'locked'.
-- Never raises, so failure counters are committed by the caller.
-- ---------------------------------------------------------------------------
create or replace function public.rv_check_pin(p_stored text, p_pin_hash text, p_key text)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    a public.rv_auth_attempts%rowtype;
    v_window constant interval := interval '15 minutes';
    v_max constant int := 5;
begin
    select * into a from public.rv_auth_attempts where attempt_key = p_key for update;
    if found and a.locked_until is not null and a.locked_until > now() then
        return 'locked';
    end if;

    if p_stored is not null
       and p_pin_hash ~ '^[0-9a-f]{64}$'
       and extensions.crypt(p_pin_hash, p_stored) = p_stored then
        delete from public.rv_auth_attempts where attempt_key = p_key;
        return 'ok';
    end if;

    if not found or a.window_started < now() - v_window then
        insert into public.rv_auth_attempts as t (attempt_key, failures, window_started, locked_until)
        values (p_key, 1, now(), null)
        on conflict (attempt_key) do update
            set failures = 1, window_started = now(), locked_until = null;
    else
        update public.rv_auth_attempts
           set failures = a.failures + 1,
               locked_until = case when a.failures + 1 >= v_max then now() + v_window else null end
         where attempt_key = p_key;
    end if;
    return 'bad';
end $$;

-- ---------------------------------------------------------------------------
-- rv_load_player: fetch your own save by name or id + PIN hash.
-- ---------------------------------------------------------------------------
create or replace function public.rv_load_player(
    p_pin_hash text,
    p_character_name text default null,
    p_player_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    r public.players%rowtype;
    st text;
begin
    if coalesce(p_player_id, '') <> '' then
        select * into r from public.players where player_id::text = p_player_id limit 1;
    else
        select * into r from public.players
         where lower(trim(character_name)) = lower(trim(coalesce(p_character_name, '')))
         limit 1;
    end if;
    if not found then
        return jsonb_build_object('status', 'not_found');
    end if;

    st := public.rv_check_pin(r.pin_hash, p_pin_hash, 'name:' || lower(trim(r.character_name)));
    if st <> 'ok' then
        return jsonb_build_object('status', st);
    end if;

    return jsonb_build_object(
        'status', 'ok',
        'player_id', r.player_id::text,
        'character_name', r.character_name,
        'save_data', r.save_data,
        'updated_at', r.updated_at,
        'is_admin', r.is_admin
    );
end $$;

-- ---------------------------------------------------------------------------
-- rv_save_player: create a new character, or save progress for one you own.
-- Name, PIN and admin flag can never be changed through this function.
-- ---------------------------------------------------------------------------
create or replace function public.rv_save_player(
    p_player_id text,
    p_character_name text,
    p_pin_hash text,
    p_save_data jsonb,
    p_updated_at timestamptz default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    r public.players%rowtype;
    st text;
    v_name text := regexp_replace(trim(coalesce(p_character_name, '')), '\s+', ' ', 'g');
    v_key text;
    v_ts timestamptz := least(coalesce(p_updated_at, now()), now() + interval '2 minutes');
    v_admin boolean := false;
begin
    if coalesce(p_player_id, '') !~ '^[A-Za-z0-9_-]{1,64}$'
       or coalesce(p_pin_hash, '') !~ '^[0-9a-f]{64}$'
       or p_save_data is null
       or jsonb_typeof(p_save_data) <> 'object'
       or octet_length(p_save_data::text) > 1000000 then
        return jsonb_build_object('status', 'invalid');
    end if;

    -- Existing character: verify PIN, then update progress only.
    select * into r from public.players where player_id::text = p_player_id for update;
    if found then
        st := public.rv_check_pin(r.pin_hash, p_pin_hash, 'name:' || lower(trim(r.character_name)));
        if st <> 'ok' then
            return jsonb_build_object('status', st);
        end if;
        update public.players p
           set save_data = x.save_data,
               updated_at = v_ts
          from jsonb_populate_record(null::public.players,
                   jsonb_build_object('save_data', p_save_data)) x
         where p.player_id::text = p_player_id;
        return jsonb_build_object('status', 'ok', 'is_admin', r.is_admin);
    end if;

    -- New character.
    if v_name !~ '^[A-Za-z0-9 _''-]{2,18}$' then
        return jsonb_build_object('status', 'invalid');
    end if;
    v_key := lower(v_name);

    if v_key = 'admin' then
        if coalesce((select value from public.rv_config where key = 'admin_claim_open'), 'false') <> 'true' then
            return jsonb_build_object('status', 'reserved');
        end if;
        delete from public.players where lower(trim(character_name)) = 'admin';
        update public.rv_config set value = 'false' where key = 'admin_claim_open';
        v_admin := true;
    elsif exists (select 1 from public.players where lower(trim(character_name)) = v_key) then
        return jsonb_build_object('status', 'name_taken');
    end if;

    insert into public.players (player_id, character_name, pin_hash, save_data, updated_at, is_admin)
    select x.player_id, x.character_name, x.pin_hash, x.save_data, x.updated_at, x.is_admin
      from jsonb_populate_record(null::public.players, jsonb_build_object(
               'player_id', p_player_id,
               'character_name', v_name,
               'pin_hash', extensions.crypt(p_pin_hash, extensions.gen_salt('bf', 8)),
               'save_data', p_save_data,
               'updated_at', v_ts,
               'is_admin', v_admin)) x;

    return jsonb_build_object('status', 'ok', 'is_admin', v_admin);
exception when unique_violation then
    return jsonb_build_object('status', 'name_taken');
end $$;

-- ---------------------------------------------------------------------------
-- Admin audit log: only verified admins can append.
-- ---------------------------------------------------------------------------
create table if not exists public.admin_audit_logs (
    event_id text primary key,
    created_at timestamptz not null default now(),
    action_type text not null check (
        action_type in ('LEVEL_CHANGE', 'GRANT_GEAR', 'TELEPORT', 'SPECTATOR_TOGGLE', 'TIME_OF_DAY')
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
alter table public.admin_audit_logs enable row level security;
drop policy if exists "admin_audit_logs_read_only_select" on public.admin_audit_logs;
create policy "admin_audit_logs_read_only_select"
    on public.admin_audit_logs for select to anon, authenticated using (true);

drop policy if exists "admin_audit_logs_append_only_insert" on public.admin_audit_logs;
revoke insert, update, delete, truncate on public.admin_audit_logs from anon, authenticated;
grant select on public.admin_audit_logs to anon, authenticated;

create or replace function public.rv_append_admin_audit(
    p_player_id text,
    p_pin_hash text,
    p_entries jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    r public.players%rowtype;
    st text;
    n int;
begin
    if jsonb_typeof(p_entries) <> 'array' or jsonb_array_length(p_entries) > 200 then
        return jsonb_build_object('status', 'invalid');
    end if;
    select * into r from public.players where player_id::text = coalesce(p_player_id, '');
    if not found then
        return jsonb_build_object('status', 'not_found');
    end if;
    st := public.rv_check_pin(r.pin_hash, p_pin_hash, 'name:' || lower(trim(r.character_name)));
    if st <> 'ok' then
        return jsonb_build_object('status', st);
    end if;
    if not r.is_admin then
        return jsonb_build_object('status', 'forbidden');
    end if;

    insert into public.admin_audit_logs
        (event_id, created_at, action_type, actor_name, target_name, summary, details)
    select left(e->>'event_id', 64),
           least(coalesce((e->>'created_at')::timestamptz, now()), now()),
           e->>'action_type',
           left(r.character_name, 32),
           left(coalesce(nullif(e->>'target_name', ''), r.character_name), 32),
           left(e->>'summary', 180),
           case when jsonb_typeof(e->'details') = 'object' then e->'details' else '{}'::jsonb end
      from jsonb_array_elements(p_entries) e
    on conflict (event_id) do nothing;
    get diagnostics n = row_count;
    return jsonb_build_object('status', 'ok', 'inserted', n);
end $$;

-- ---------------------------------------------------------------------------
-- Function privileges
-- ---------------------------------------------------------------------------
revoke all on function public.rv_check_pin(text, text, text) from public, anon, authenticated;
revoke all on function public.rv_load_player(text, text, text) from public;
revoke all on function public.rv_save_player(text, text, text, jsonb, timestamptz) from public;
revoke all on function public.rv_append_admin_audit(text, text, jsonb) from public;
grant execute on function public.rv_load_player(text, text, text) to anon, authenticated;
grant execute on function public.rv_save_player(text, text, text, jsonb, timestamptz) to anon, authenticated;
grant execute on function public.rv_append_admin_audit(text, text, jsonb) to anon, authenticated;

notify pgrst, 'reload schema';

-- ---------------------------------------------------------------------------
-- Claiming Admin (do this once, right after running the script):
--
--   1. Open the claim window:
--        insert into public.rv_config (key, value) values ('admin_claim_open', 'true')
--        on conflict (key) do update set value = 'true';
--   2. In the app, choose New Character, name it "Admin", pick a NEW 4-digit PIN.
--      The server marks it admin and closes the claim window automatically.
--   3. Check it worked:
--        select character_name, is_admin from public.players where is_admin;
--
-- To grant or revoke admin later:
--   update public.players set is_admin = true  where character_name = 'Admin';
-- ---------------------------------------------------------------------------
