-- SkillSwap AI database setup
-- Run this entire script in Supabase Dashboard -> SQL Editor -> New query.
-- This schema stores only display names and learning skills. Do not add phone numbers,
-- home addresses, exact school/class details, or other private student information.

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 24),
  teaches text not null check (char_length(teaches) between 1 and 35),
  learns text not null check (char_length(learns) between 1 and 35),
  category text not null default 'Creative' check (category in ('Design','Technology','Creative','Academic')),
  about text not null default '' check (char_length(about) <= 140),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.swap_requests (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.profiles(id) on delete cascade,
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  skill text not null check (char_length(skill) between 1 and 60),
  status text not null default 'pending' check (status in ('pending','accepted','declined')),
  created_at timestamptz not null default now(),
  constraint no_self_swap check (sender_id <> recipient_id)
);

create index if not exists profiles_created_at_idx on public.profiles(created_at desc);
create index if not exists swap_requests_sender_idx on public.swap_requests(sender_id);
create index if not exists swap_requests_recipient_idx on public.swap_requests(recipient_id);

alter table public.profiles enable row level security;
alter table public.swap_requests enable row level security;

drop policy if exists "Signed-in members can view profiles" on public.profiles;
create policy "Signed-in members can view profiles"
on public.profiles for select to authenticated using (true);

drop policy if exists "Members can create their own profile" on public.profiles;
create policy "Members can create their own profile"
on public.profiles for insert to authenticated with check (auth.uid() = id);

drop policy if exists "Members can edit their own profile" on public.profiles;
create policy "Members can edit their own profile"
on public.profiles for update to authenticated
using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "Members can delete their own profile" on public.profiles;
create policy "Members can delete their own profile"
on public.profiles for delete to authenticated using (auth.uid() = id);

drop policy if exists "Members can view their own swap requests" on public.swap_requests;
create policy "Members can view their own swap requests"
on public.swap_requests for select to authenticated
using (auth.uid() = sender_id or auth.uid() = recipient_id);

drop policy if exists "Members can send swap requests as themselves" on public.swap_requests;
create policy "Members can send swap requests as themselves"
on public.swap_requests for insert to authenticated
with check (auth.uid() = sender_id and auth.uid() <> recipient_id);

-- Deliberately no public access and no update/delete policy for requests in this first version.
-- Add teacher/moderator approval before inviting a real student community.
