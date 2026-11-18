-- ==============================================================================
-- SPENDWISE DATABASE SCHEMA & CONFIGURATION FOR SUPABASE
-- Personal Finance & Travel Tracker (Web + Native Android)
-- ==============================================================================

-- 1. Enable Required Extensions
create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

-- 2. Drop existing tables to cleanly migrate to flexible schema
drop table if exists public.budgets cascade;
drop table if exists public.transactions cascade;
drop table if exists public.trips cascade;
drop table if exists public.categories cascade;

-- 3. Create Categories Table
-- Supports both default slugs ('cat-food') and custom user categories
create table public.categories (
    id text primary key default gen_random_uuid()::text,
    user_id text,
    name text not null,
    type text not null check (type in ('expense', 'income')),
    icon text not null default 'Tag',
    color text not null default '#6366F1',
    is_default boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 4. Create Trips Table (Travel Plans)
create table public.trips (
    id text primary key default gen_random_uuid()::text,
    user_id text not null,
    destination text not null,
    start_date date not null,
    end_date date not null,
    budget numeric(12, 2) not null default 0 check (budget >= 0),
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (end_date >= start_date)
);

-- 5. Create Transactions Table
create table public.transactions (
    id text primary key default gen_random_uuid()::text,
    user_id text not null,
    category_id text references public.categories(id) on delete set null,
    trip_id text references public.trips(id) on delete set null,
    type text not null check (type in ('expense', 'income')),
    amount numeric(12, 2) not null check (amount > 0),
    date date not null default current_date,
    note text,
    payment_method text not null default 'upi' check (payment_method in ('cash', 'upi', 'card', 'bank_transfer', 'net_banking')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 6. Create Budgets Table (Monthly category budgets)
create table public.budgets (
    id text primary key default gen_random_uuid()::text,
    user_id text not null,
    category_id text references public.categories(id) on delete cascade not null,
    month integer not null check (month between 1 and 12),
    year integer not null check (year between 2000 and 2100),
    amount numeric(12, 2) not null check (amount >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, category_id, month, year)
);

-- 7. Indexes for High Performance Queries
create index if not exists idx_categories_user on public.categories(user_id);
create index if not exists idx_transactions_user_date on public.transactions(user_id, date desc);
create index if not exists idx_transactions_category on public.transactions(category_id);
create index if not exists idx_transactions_trip on public.transactions(trip_id);
create index if not exists idx_budgets_user_period on public.budgets(user_id, year, month);
create index if not exists idx_trips_user on public.trips(user_id);

-- 8. Updated At Trigger Function
create or replace function public.handle_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

drop trigger if exists trg_categories_updated_at on public.categories;
create trigger trg_categories_updated_at
    before update on public.categories
    for each row execute function public.handle_updated_at();

drop trigger if exists trg_trips_updated_at on public.trips;
create trigger trg_trips_updated_at
    before update on public.trips
    for each row execute function public.handle_updated_at();

drop trigger if exists trg_transactions_updated_at on public.transactions;
create trigger trg_transactions_updated_at
    before update on public.transactions
    for each row execute function public.handle_updated_at();

drop trigger if exists trg_budgets_updated_at on public.budgets;
create trigger trg_budgets_updated_at
    before update on public.budgets
    for each row execute function public.handle_updated_at();

-- 9. Open Read & Write Access (Never block client app storing)
alter table public.categories disable row level security;
alter table public.trips disable row level security;
alter table public.transactions disable row level security;
alter table public.budgets disable row level security;

-- 10. Direct Seed Global Default Categories (Accessible by all users)
insert into public.categories (id, user_id, name, type, icon, color, is_default) values
    ('cat-food', null, 'Food & Dining', 'expense', 'Utensils', '#EF4444', true),
    ('cat-trans', null, 'Transportation', 'expense', 'Car', '#F59E0B', true),
    ('cat-shop', null, 'Shopping', 'expense', 'ShoppingBag', '#EC4899', true),
    ('cat-ent', null, 'Entertainment', 'expense', 'Film', '#8B5CF6', true),
    ('cat-bills', null, 'Bills & Utilities', 'expense', 'Zap', '#3B82F6', true),
    ('cat-health', null, 'Healthcare', 'expense', 'HeartPulse', '#10B981', true),
    ('cat-travel', null, 'Travel', 'expense', 'Plane', '#06B6D4', true),
    ('cat-groc', null, 'Groceries', 'expense', 'ShoppingCart', '#84CC16', true),
    ('cat-edu', null, 'Education', 'expense', 'GraduationCap', '#6366F1', true),
    ('cat-other-exp', null, 'Other Expense', 'expense', 'MoreHorizontal', '#64748B', true),
    ('cat-sal', null, 'Salary', 'income', 'Briefcase', '#10B981', true),
    ('cat-inv', null, 'Investment', 'income', 'TrendingUp', '#059669', true),
    ('cat-free', null, 'Freelance', 'income', 'Laptop', '#34D399', true),
    ('cat-gift', null, 'Gift / Bonus', 'income', 'Gift', '#2DD4BF', true),
    ('cat-other-inc', null, 'Other Income', 'income', 'PlusCircle', '#14B8A6', true)
on conflict (id) do update set
    name = excluded.name,
    type = excluded.type,
    icon = excluded.icon,
    color = excluded.color,
    is_default = excluded.is_default;

-- 11. Enable Supabase Realtime
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

-- 12. Over-The-Air (OTA) App In-App Updates System
create table if not exists public.app_updates (
    id uuid primary key default gen_random_uuid(),
    version_code integer not null unique,
    version_name text not null,
    apk_url text not null,
    changelog text,
    is_mandatory boolean not null default false,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

alter table public.app_updates disable row level security;
alter table public.app_updates replica identity full;

-- Grant public access so any installed Android app can check for latest release
grant select on table public.app_updates to anon, authenticated;

-- Release v1.0.1 (Excel / CSV Report Export & OTA in-app updater)
insert into public.app_updates (version_code, version_name, apk_url, changelog, is_mandatory)
values (
    2,
    '1.0.1',
    'https://raw.githubusercontent.com/Mathidevdevil/Expancetraker/main/release/spendwise-v1.0.1.apk',
    '• New Feature: Excel Sheet (.xls) and CSV statement export\n• Download Monthly, Yearly, and Date-wise financial reports\n• Live transaction preview & cashflow summary before export\n• 1-Tap sharing to WhatsApp, Google Drive, and Excel\n• Apple SF Pro typography & performance optimizations',
    false
)
on conflict (version_code) do update set
    apk_url = excluded.apk_url,
    changelog = excluded.changelog,
    version_name = excluded.version_name,
    is_mandatory = excluded.is_mandatory;

