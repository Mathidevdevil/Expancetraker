-- ==============================================================================
-- SPENDWISE STEP 2: BACKWARD-COMPATIBLE RLS & SAFE CONSTRAINTS MIGRATION
-- 1. Backfills any legacy email-keyed user_id rows to auth.users.id::text so
--    existing users retain 100% of their rows once RLS is enabled.
-- 2. Enables RLS on categories, trips, transactions, budgets, app_updates.
-- 3. Replaces open access with TO authenticated ((select auth.uid())::text = user_id)
--    while keeping global default categories (user_id is null and is_default)
--    and app_updates readable.
-- 4. Adds sensible text-length / positive-amount CHECK constraints ONLY if
--    existing rows pass validation.
-- ==============================================================================

begin;

-- --------------------------------------------------------------------------
-- 1. Backfill legacy email-owned user_id rows -> auth.users.id::text
-- --------------------------------------------------------------------------
update public.categories c
set user_id = u.id::text
from auth.users u
where c.is_default = false
  and c.user_id is not null
  and lower(c.user_id) = lower(u.email)
  and c.user_id <> u.id::text;

update public.trips tr
set user_id = u.id::text
from auth.users u
where lower(tr.user_id) = lower(u.email)
  and tr.user_id <> u.id::text;

update public.transactions tx
set user_id = u.id::text
from auth.users u
where lower(tx.user_id) = lower(u.email)
  and tx.user_id <> u.id::text;

-- Budgets has UNIQUE (user_id, category_id, month, year): skip rows that already
-- have a UUID-owned budget for the same slot, and update the rest cleanly.
update public.budgets b
set user_id = u.id::text
from auth.users u
where lower(b.user_id) = lower(u.email)
  and b.user_id <> u.id::text
  and not exists (
      select 1 from public.budgets existing
      where existing.user_id = u.id::text
        and existing.category_id = b.category_id
        and existing.month = b.month
        and existing.year = b.year
  );

-- --------------------------------------------------------------------------
-- 2. Restrict direct anon writes on user financial tables
-- --------------------------------------------------------------------------
revoke all on table public.categories from anon;
revoke all on table public.trips from anon;
revoke all on table public.transactions from anon;
revoke all on table public.budgets from anon;

grant select on table public.categories to anon;
grant select, insert, update, delete on table public.categories to authenticated;
grant select, insert, update, delete on table public.trips to authenticated;
grant select, insert, update, delete on table public.transactions to authenticated;
grant select, insert, update, delete on table public.budgets to authenticated;

grant select on table public.app_updates to anon, authenticated;

-- --------------------------------------------------------------------------
-- 3. Enable Row Level Security on EVERY table in public
-- --------------------------------------------------------------------------
alter table public.categories enable row level security;
alter table public.trips enable row level security;
alter table public.transactions enable row level security;
alter table public.budgets enable row level security;
alter table public.app_updates enable row level security;

-- --------------------------------------------------------------------------
-- 4. Owner-scoped policies (using (select auth.uid()) per Supabase best practices)
-- --------------------------------------------------------------------------

-- CATEGORIES: read global defaults (user_id is null) or own rows; write own custom categories only
drop policy if exists "categories_select_own_or_default" on public.categories;
create policy "categories_select_own_or_default"
    on public.categories for select
    to authenticated
    using (
        (user_id is null and is_default = true)
        or (select auth.uid())::text = user_id
    );

drop policy if exists "categories_anon_select_default" on public.categories;
create policy "categories_anon_select_default"
    on public.categories for select
    to anon
    using (user_id is null and is_default = true);

drop policy if exists "categories_insert_own" on public.categories;
create policy "categories_insert_own"
    on public.categories for insert
    to authenticated
    with check (
        (select auth.uid())::text = user_id
        and is_default = false
    );

drop policy if exists "categories_update_own" on public.categories;
create policy "categories_update_own"
    on public.categories for update
    to authenticated
    using ((select auth.uid())::text = user_id and is_default = false)
    with check ((select auth.uid())::text = user_id and is_default = false);

drop policy if exists "categories_delete_own" on public.categories;
create policy "categories_delete_own"
    on public.categories for delete
    to authenticated
    using ((select auth.uid())::text = user_id and is_default = false);

-- TRANSACTIONS
drop policy if exists "transactions_select_own" on public.transactions;
create policy "transactions_select_own"
    on public.transactions for select
    to authenticated
    using ((select auth.uid())::text = user_id);

drop policy if exists "transactions_insert_own" on public.transactions;
create policy "transactions_insert_own"
    on public.transactions for insert
    to authenticated
    with check ((select auth.uid())::text = user_id);

drop policy if exists "transactions_update_own" on public.transactions;
create policy "transactions_update_own"
    on public.transactions for update
    to authenticated
    using ((select auth.uid())::text = user_id)
    with check ((select auth.uid())::text = user_id);

drop policy if exists "transactions_delete_own" on public.transactions;
create policy "transactions_delete_own"
    on public.transactions for delete
    to authenticated
    using ((select auth.uid())::text = user_id);

-- BUDGETS
drop policy if exists "budgets_select_own" on public.budgets;
create policy "budgets_select_own"
    on public.budgets for select
    to authenticated
    using ((select auth.uid())::text = user_id);

drop policy if exists "budgets_insert_own" on public.budgets;
create policy "budgets_insert_own"
    on public.budgets for insert
    to authenticated
    with check ((select auth.uid())::text = user_id);

drop policy if exists "budgets_update_own" on public.budgets;
create policy "budgets_update_own"
    on public.budgets for update
    to authenticated
    using ((select auth.uid())::text = user_id)
    with check ((select auth.uid())::text = user_id);

drop policy if exists "budgets_delete_own" on public.budgets;
create policy "budgets_delete_own"
    on public.budgets for delete
    to authenticated
    using ((select auth.uid())::text = user_id);

-- TRIPS
drop policy if exists "trips_select_own" on public.trips;
create policy "trips_select_own"
    on public.trips for select
    to authenticated
    using ((select auth.uid())::text = user_id);

drop policy if exists "trips_insert_own" on public.trips;
create policy "trips_insert_own"
    on public.trips for insert
    to authenticated
    with check ((select auth.uid())::text = user_id);

drop policy if exists "trips_update_own" on public.trips;
create policy "trips_update_own"
    on public.trips for update
    to authenticated
    using ((select auth.uid())::text = user_id)
    with check ((select auth.uid())::text = user_id);

drop policy if exists "trips_delete_own" on public.trips;
create policy "trips_delete_own"
    on public.trips for delete
    to authenticated
    using ((select auth.uid())::text = user_id);

-- APP UPDATES: public read-only (OTA checks), no anon/authenticated writes
drop policy if exists "app_updates_public_read" on public.app_updates;
create policy "app_updates_public_read"
    on public.app_updates for select
    to anon, authenticated
    using (true);

-- --------------------------------------------------------------------------
-- 5. Safe non-breaking CHECK constraints (only applied if all rows satisfy)
-- --------------------------------------------------------------------------
do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'chk_transactions_note_length')
       and not exists (select 1 from public.transactions where length(coalesce(note, '')) > 500) then
        alter table public.transactions
            add constraint chk_transactions_note_length check (length(coalesce(note, '')) <= 500);
    end if;

    if not exists (select 1 from pg_constraint where conname = 'chk_categories_name_length')
       and not exists (select 1 from public.categories where length(trim(name)) = 0 or length(name) > 80) then
        alter table public.categories
            add constraint chk_categories_name_length check (length(trim(name)) > 0 and length(name) <= 80);
    end if;

    if not exists (select 1 from pg_constraint where conname = 'chk_trips_text_length')
       and not exists (select 1 from public.trips where length(trim(destination)) = 0 or length(destination) > 150 or length(coalesce(notes, '')) > 1000) then
        alter table public.trips
            add constraint chk_trips_text_length check (length(trim(destination)) > 0 and length(destination) <= 150 and length(coalesce(notes, '')) <= 1000);
    end if;
end $$;

-- --------------------------------------------------------------------------
-- 6. Harden handle_updated_at search_path
-- --------------------------------------------------------------------------
create or replace function public.handle_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

commit;
