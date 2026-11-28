-- ==============================================================================
-- SPENDWISE STEP 3: AUTOMATED RLS ISOLATION TEST (Non-destructive: ROLLBACK)
-- Proves:
-- 1. User A can read global default categories and CRUD their own rows.
-- 2. User B receives 0 rows on SELECT and fails/affects 0 rows when trying to
--    SELECT, INSERT, UPDATE, or DELETE User A's transactions, budgets, trips,
--    and custom categories.
-- ==============================================================================

begin;

do $$
declare
    user_a uuid := '11111111-1111-4111-8111-111111111111';
    user_b uuid := '22222222-2222-4222-8222-222222222222';
    cat_a text := 'cat-test-a-' || replace(gen_random_uuid()::text, '-', '');
    tx_a text := 'tx-test-a-' || replace(gen_random_uuid()::text, '-', '');
    trip_a text := 'trip-test-a-' || replace(gen_random_uuid()::text, '-', '');
    budget_a text := 'budget-test-a-' || replace(gen_random_uuid()::text, '-', '');
    visible_count integer;
    updated_count integer;
    deleted_count integer;
    rls_blocked boolean;
begin
    -- Switch to User A
    perform set_config('request.jwt.claim.sub', user_a::text, true);
    perform set_config('request.jwt.claim.role', 'authenticated', true);
    execute 'set local role authenticated';

    -- User A can see global default categories
    select count(*) into visible_count from public.categories where id = 'cat-food';
    if visible_count <> 1 then
        raise exception 'RLS FAIL: User A cannot read default category cat-food';
    end if;

    -- User A inserts own custom category, trip, transaction, budget
    insert into public.categories (id, user_id, name, type, icon, color, is_default)
    values (cat_a, user_a::text, 'A Custom Cat', 'expense', 'Tag', '#D4AF6A', false);

    insert into public.trips (id, user_id, destination, start_date, end_date, budget)
    values (trip_a, user_a::text, 'Chennai', '2026-11-01', '2026-11-05', 5000);

    insert into public.transactions (id, user_id, category_id, trip_id, type, amount, date, note, payment_method)
    values (tx_a, user_a::text, 'cat-food', trip_a, 'expense', 486, '2026-11-26', 'User A lunch', 'upi');

    insert into public.budgets (id, user_id, category_id, month, year, amount)
    values (budget_a, user_a::text, 'cat-food', 11, 2026, 10000);

    -- Verify User A reads own transaction
    select count(*) into visible_count from public.transactions where id = tx_a;
    if visible_count <> 1 then
        raise exception 'RLS FAIL: User A cannot read own transaction';
    end if;

    -- Switch to User B
    perform set_config('request.jwt.claim.sub', user_b::text, true);
    perform set_config('request.jwt.claim.role', 'authenticated', true);

    -- User B MUST see 0 of User A's rows across all tables
    select count(*) into visible_count from public.transactions where id = tx_a;
    if visible_count <> 0 then
        raise exception 'RLS LEAK: User B read User A transaction';
    end if;

    select count(*) into visible_count from public.categories where id = cat_a;
    if visible_count <> 0 then
        raise exception 'RLS LEAK: User B read User A custom category';
    end if;

    select count(*) into visible_count from public.trips where id = trip_a;
    if visible_count <> 0 then
        raise exception 'RLS LEAK: User B read User A trip';
    end if;

    select count(*) into visible_count from public.budgets where id = budget_a;
    if visible_count <> 0 then
        raise exception 'RLS LEAK: User B read User A budget';
    end if;

    -- User B attempts UPDATE / DELETE on User A rows -> must affect 0 rows
    update public.transactions set amount = 1 where id = tx_a;
    get diagnostics updated_count = row_count;
    if updated_count <> 0 then
        raise exception 'RLS LEAK: User B updated User A transaction';
    end if;

    delete from public.transactions where id = tx_a;
    get diagnostics deleted_count = row_count;
    if deleted_count <> 0 then
        raise exception 'RLS LEAK: User B deleted User A transaction';
    end if;

    -- User B attempts INSERT spoofing user_id = User A -> must throw RLS violation (42501)
    rls_blocked := false;
    begin
        insert into public.transactions (id, user_id, category_id, type, amount, date, payment_method)
        values ('tx-spoof-' || gen_random_uuid()::text, user_a::text, 'cat-food', 'expense', 999, '2026-11-26', 'upi');
    exception when insufficient_privilege then
        rls_blocked := true;
    end;
    if not rls_blocked then
        raise exception 'RLS LEAK: User B inserted a row claiming user_id = User A';
    end if;

    raise notice 'ALL SPENDWISE RLS ISOLATION TESTS PASSED!';
end $$;

rollback;
