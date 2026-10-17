-- ReelCount Phase 2. Run once in the Supabase SQL editor.

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  daily_limit integer not null default 50 check (daily_limit > 0),
  locale text not null default 'en' check (locale in ('en', 'bn')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.daily_counts (
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  day date not null,
  app text not null,
  reels_count integer not null default 0 check (reels_count >= 0),
  est_seconds integer not null default 0 check (est_seconds >= 0),
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id, day, app)
);

create table if not exists public.hourly_counts (
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  day date not null,
  hour smallint not null check (hour between 0 and 23),
  app text not null,
  reels_count integer not null default 0 check (reels_count >= 0),
  primary key (user_id, device_id, day, hour, app)
);

create index if not exists daily_counts_user_day_idx on public.daily_counts (user_id, day);
create index if not exists hourly_counts_user_day_idx on public.hourly_counts (user_id, day);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

drop trigger if exists daily_counts_set_updated_at on public.daily_counts;
create trigger daily_counts_set_updated_at
  before update on public.daily_counts
  for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name, daily_limit, locale)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1)),
    50,
    coalesce(new.raw_user_meta_data->>'locale', 'en')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.daily_counts enable row level security;
alter table public.hourly_counts enable row level security;

drop policy if exists "profiles select own" on public.profiles;
create policy "profiles select own" on public.profiles for select using (auth.uid() = id);
drop policy if exists "profiles update own" on public.profiles;
create policy "profiles update own" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "daily select own" on public.daily_counts;
create policy "daily select own" on public.daily_counts for select using (auth.uid() = user_id);
drop policy if exists "daily insert own" on public.daily_counts;
create policy "daily insert own" on public.daily_counts for insert with check (auth.uid() = user_id);
drop policy if exists "daily update own" on public.daily_counts;
create policy "daily update own" on public.daily_counts for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists "daily delete own" on public.daily_counts;
create policy "daily delete own" on public.daily_counts for delete using (auth.uid() = user_id);

drop policy if exists "hourly select own" on public.hourly_counts;
create policy "hourly select own" on public.hourly_counts for select using (auth.uid() = user_id);
drop policy if exists "hourly insert own" on public.hourly_counts;
create policy "hourly insert own" on public.hourly_counts for insert with check (auth.uid() = user_id);
drop policy if exists "hourly update own" on public.hourly_counts;
create policy "hourly update own" on public.hourly_counts for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists "hourly delete own" on public.hourly_counts;
create policy "hourly delete own" on public.hourly_counts for delete using (auth.uid() = user_id);
