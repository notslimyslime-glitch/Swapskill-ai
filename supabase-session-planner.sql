-- SkillSwap AI: learning session planner
-- Run once in Supabase Dashboard -> SQL Editor -> New query.
-- Only participants in an accepted swap can view or edit its plan.

create table if not exists public.swap_plans (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null unique references public.swap_requests(id) on delete cascade,
  goal text not null default '' check (char_length(goal) <= 180),
  session_at timestamptz,
  tasks jsonb not null default '[]'::jsonb check (jsonb_typeof(tasks) = 'array'),
  updated_by uuid not null references auth.users(id),
  updated_at timestamptz not null default now()
);

create index if not exists swap_plans_request_id_idx
  on public.swap_plans(request_id);

alter table public.swap_plans enable row level security;

grant select, insert, update on public.swap_plans to authenticated;

drop policy if exists "Swap participants can view accepted swap plans" on public.swap_plans;
create policy "Swap participants can view accepted swap plans"
on public.swap_plans for select to authenticated
using (
  exists (
    select 1 from public.swap_requests sr
    where sr.id = request_id
      and sr.status = 'accepted'
      and auth.uid() in (sr.sender_id, sr.recipient_id)
  )
);

drop policy if exists "Swap participants can create accepted swap plans" on public.swap_plans;
create policy "Swap participants can create accepted swap plans"
on public.swap_plans for insert to authenticated
with check (
  updated_by = auth.uid()
  and exists (
    select 1 from public.swap_requests sr
    where sr.id = request_id
      and sr.status = 'accepted'
      and auth.uid() in (sr.sender_id, sr.recipient_id)
  )
);

drop policy if exists "Swap participants can update accepted swap plans" on public.swap_plans;
create policy "Swap participants can update accepted swap plans"
on public.swap_plans for update to authenticated
using (
  exists (
    select 1 from public.swap_requests sr
    where sr.id = request_id
      and sr.status = 'accepted'
      and auth.uid() in (sr.sender_id, sr.recipient_id)
  )
)
with check (
  updated_by = auth.uid()
  and exists (
    select 1 from public.swap_requests sr
    where sr.id = request_id
      and sr.status = 'accepted'
      and auth.uid() in (sr.sender_id, sr.recipient_id)
  )
);

-- The app stores task labels and each participant's own completion flag.
-- Never put phone numbers, addresses, or private contact details in a plan.
