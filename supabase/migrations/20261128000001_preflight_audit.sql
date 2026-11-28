-- ==============================================================================
-- SPENDWISE STEP 1: READ-ONLY PRE-FLIGHT DATA & RLS AUDIT
-- Run this query BEFORE applying 20261128000002_enable_rls_and_policies.sql.
-- Verify what legacy user_id formats exist and confirm zero rows violate CHECKs.
-- ==============================================================================

-- 1. Check current RLS state on all public tables
select
    schemaname,
    tablename,
    rowsecurity as rls_enabled
from pg_tables
where schemaname = 'public'
  and tablename in ('categories', 'trips', 'transactions', 'budgets', 'app_updates')
order by tablename;

-- 2. Inspect distinct user_id styles (UUID vs email vs legacy placeholder)
select 'transactions' as tbl,
       count(*) as total_rows,
       count(*) filter (where user_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') as uuid_rows,
       count(*) filter (where user_id like '%@%') as email_rows,
       count(*) filter (where user_id is null or user_id = '' or user_id = 'demo-user') as other_rows
from public.transactions
union all
select 'budgets',
       count(*),
       count(*) filter (where user_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
       count(*) filter (where user_id like '%@%'),
       count(*) filter (where user_id is null or user_id = '' or user_id = 'demo-user')
from public.budgets
union all
select 'trips',
       count(*),
       count(*) filter (where user_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
       count(*) filter (where user_id like '%@%'),
       count(*) filter (where user_id is null or user_id = '' or user_id = 'demo-user')
from public.trips
union all
select 'categories_custom',
       count(*),
       count(*) filter (where user_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
       count(*) filter (where user_id like '%@%'),
       count(*) filter (where user_id is null or user_id = '' or user_id = 'demo-user')
from public.categories
where is_default = false;

-- 3. Check for rows that would fail sensible length/value CHECK constraints
select 'bad_tx_amount' as check_name, count(*) as violations
from public.transactions where amount <= 0
union all
select 'long_tx_note', count(*) from public.transactions where length(coalesce(note, '')) > 500
union all
select 'bad_budget_amount', count(*) from public.budgets where amount < 0
union all
select 'bad_trip_budget', count(*) from public.trips where budget < 0
union all
select 'long_trip_dest', count(*) from public.trips where length(destination) > 150 or length(coalesce(notes, '')) > 1000
union all
select 'long_cat_name', count(*) from public.categories where length(name) > 80;
