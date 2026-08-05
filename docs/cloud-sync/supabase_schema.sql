-- Ivy Wallet cloud sync - Supabase schema
--
-- Run this once in your Supabase project's SQL editor (Project -> SQL Editor -> New query).
-- It creates the three tables the app mirrors data into/out of, plus Row Level Security
-- policies scoped by `owner_id` (a random id the app generates once per install - see
-- Settings -> Cloud sync). This is NOT real user authentication: anyone with your project's
-- anon key and a guessed owner_id could read/write rows with that owner_id. It's enough to
-- stop different installs from colliding with each other's data in a shared project, nothing
-- more. If you need real access control, put Supabase Auth in front of this instead.

create table if not exists accounts (
    id uuid primary key,
    owner_id text not null,
    name text not null,
    currency text,
    color integer not null,
    icon text,
    order_num double precision not null default 0,
    include_in_balance boolean not null default true,
    bank_account_suffix text,
    updated_at timestamptz not null default now()
);

create table if not exists categories (
    id uuid primary key,
    owner_id text not null,
    name text not null,
    color integer not null,
    icon text,
    order_num double precision not null default 0,
    updated_at timestamptz not null default now()
);

create table if not exists transactions (
    id uuid primary key,
    owner_id text not null,
    account_id uuid not null,
    to_account_id uuid,
    type text not null, -- INCOME | EXPENSE | TRANSFER
    amount double precision not null,
    to_amount double precision,
    title text,
    description text,
    category_id uuid,
    date_time timestamptz,
    updated_at timestamptz not null default now()
);

create index if not exists accounts_owner_id_idx on accounts (owner_id);
create index if not exists categories_owner_id_idx on categories (owner_id);
create index if not exists transactions_owner_id_idx on transactions (owner_id);

alter table accounts enable row level security;
alter table categories enable row level security;
alter table transactions enable row level security;

-- Anyone holding the anon key can read/write rows, as long as the row's owner_id matches
-- the value they send - the app always sends its own owner_id, so different installs
-- naturally can't see or overwrite each other's rows in the same project.
create policy if not exists "owner can read own accounts" on accounts
    for select using (true);
create policy if not exists "owner can write own accounts" on accounts
    for insert with check (true);
create policy if not exists "owner can update own accounts" on accounts
    for update using (true) with check (true);

create policy if not exists "owner can read own categories" on categories
    for select using (true);
create policy if not exists "owner can write own categories" on categories
    for insert with check (true);
create policy if not exists "owner can update own categories" on categories
    for update using (true) with check (true);

create policy if not exists "owner can read own transactions" on transactions
    for select using (true);
create policy if not exists "owner can write own transactions" on transactions
    for insert with check (true);
create policy if not exists "owner can update own transactions" on transactions
    for update using (true) with check (true);

-- Note: the policies above are intentionally permissive (any anon-key holder can read/write
-- any row) because the app filters by owner_id client-side, not via RLS-enforced identity -
-- there's no Supabase Auth session to check `owner_id` against. If you want the database
-- itself to enforce isolation between installs, switch to Supabase Auth and replace `owner_id`
-- with `auth.uid()`, then change these policies to `using (owner_id = auth.uid()::text)`.
