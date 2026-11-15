-- ==============================================================================
-- SPENDWISE DATABASE SCHEMA & CONFIGURATION FOR SUPABASE
-- Personal Finance & Travel Tracker (Web + Native Android)
-- ==============================================================================

-- 1. Enable Required Extensions
create extension if not exists "uuid-ossp";

-- 2. Create Categories Table
create table if not exists public.categories (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade,
    name text not null,
    type text not null check (type in ('expense', 'income')),
    icon text not null default 'Tag',
    color text not null default '#6366F1',
    is_default boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 3. Create Trips Table (Travel Plans)
create table if not exists public.trips (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade not null,
    destination text not null,
    start_date date not null,
    end_date date not null,
    budget numeric(12, 2) not null default 0 check (budget >= 0),
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (end_date >= start_date)
);

-- 4. Create Transactions Table
create table if not exists public.transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade not null,
    category_id uuid references public.categories(id) on delete set null,
    trip_id uuid references public.trips(id) on delete set null,
    type text not null check (type in ('expense', 'income')),
    amount numeric(12, 2) not null check (amount > 0),
    date date not null default current_date,
    note text,
    payment_method text not null default 'upi' check (payment_method in ('cash', 'upi', 'card', 'bank_transfer', 'net_banking')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 5. Create Budgets Table (Monthly category budgets)
create table if not exists public.budgets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade not null,
    category_id uuid references public.categories(id) on delete cascade not null,
    month integer not null check (month between 1 and 12),
    year integer not null check (year between 2000 and 2100),
    amount numeric(12, 2) not null check (amount >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, category_id, month, year)
);

-- 6. Indexes for High Performance Queries
create index if not exists idx_categories_user on public.categories(user_id);
create index if not exists idx_transactions_user_date on public.transactions(user_id, date desc);
create index if not exists idx_transactions_category on public.transactions(category_id);
create index if not exists idx_transactions_trip on public.transactions(trip_id);
create index if not exists idx_budgets_user_period on public.budgets(user_id, year, month);
create index if not exists idx_trips_user on public.trips(user_id);

-- 7. Updated At Trigger Function
create or replace function public.handle_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

create or replace trigger trg_categories_updated_at
    before update on public.categories
    for each row execute function public.handle_updated_at();

create or replace trigger trg_trips_updated_at
    before update on public.trips
    for each row execute function public.handle_updated_at();

create or replace trigger trg_transactions_updated_at
    before update on public.transactions
    for each row execute function public.handle_updated_at();

create or replace trigger trg_budgets_updated_at
    before update on public.budgets
    for each row execute function public.handle_updated_at();

-- 8. Row Level Security (RLS)
alter table public.categories enable row level security;
alter table public.trips enable row level security;
alter table public.transactions enable row level security;
alter table public.budgets enable row level security;

-- Policies for Categories
-- Users can see their own categories and global default categories
create policy "Allow users to read own and default categories"
    on public.categories for select
    using (auth.uid() = user_id or is_default = true or user_id is null);

create policy "Allow users to insert own categories"
    on public.categories for insert
    with check (auth.uid() = user_id);

create policy "Allow users to update own categories"
    on public.categories for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "Allow users to delete own custom categories"
    on public.categories for delete
    using (auth.uid() = user_id and is_default = false);

-- Policies for Trips
create policy "Allow users full access to own trips"
    on public.trips for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- Policies for Transactions
create policy "Allow users full access to own transactions"
    on public.transactions for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- Policies for Budgets
create policy "Allow users full access to own budgets"
    on public.budgets for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- 9. Auto-seed Default Categories on User Signup
create or replace function public.seed_user_categories()
returns trigger as $$
begin
    insert into public.categories (user_id, name, type, icon, color, is_default) values
    -- Expenses
    (new.id, 'Food & Dining', 'expense', 'Utensils', '#EF4444', true),
    (new.id, 'Transportation', 'expense', 'Car', '#F59E0B', true),
    (new.id, 'Shopping', 'expense', 'ShoppingBag', '#EC4899', true),
    (new.id, 'Entertainment', 'expense', 'Film', '#8B5CF6', true),
    (new.id, 'Bills & Utilities', 'expense', 'Zap', '#3B82F6', true),
    (new.id, 'Healthcare', 'expense', 'HeartPulse', '#10B981', true),
    (new.id, 'Travel', 'expense', 'Plane', '#06B6D4', true),
    (new.id, 'Groceries', 'expense', 'ShoppingCart', '#84CC16', true),
    (new.id, 'Education', 'expense', 'GraduationCap', '#6366F1', true),
    (new.id, 'Other Expense', 'expense', 'MoreHorizontal', '#64748B', true),
    -- Incomes
    (new.id, 'Salary', 'income', 'Briefcase', '#10B981', true),
    (new.id, 'Investment', 'income', 'TrendingUp', '#059669', true),
    (new.id, 'Freelance', 'income', 'Laptop', '#34D399', true),
    (new.id, 'Gift / Bonus', 'income', 'Gift', '#2DD4BF', true),
    (new.id, 'Other Income', 'income', 'PlusCircle', '#14B8A6', true);

    return new;
end;
$$ language plpgsql security definer;

-- Trigger on auth.users creation
create or replace trigger on_auth_user_created_seed_categories
    after insert on auth.users
    for each row execute function public.seed_user_categories();

-- 10. Enable Supabase Realtime
-- Ensure tables broadcast insert, update, and delete events with full row context
alter table public.categories replica identity full;
alter table public.trips replica identity full;
alter table public.transactions replica identity full;
alter table public.budgets replica identity full;

-- Add tables to realtime publication if not already added
do $$
begin
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'categories') then
        alter publication supabase_realtime add table public.categories;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'trips') then
        alter publication supabase_realtime add table public.trips;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'transactions') then
        alter publication supabase_realtime add table public.transactions;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'budgets') then
        alter publication supabase_realtime add table public.budgets;
    end if;
end $$;
