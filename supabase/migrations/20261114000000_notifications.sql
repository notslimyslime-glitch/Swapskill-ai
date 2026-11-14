-- SkillSwap AI: in-app notification centre
-- Apply once to the hosted Supabase project. Notifications are created only by
-- database triggers; authenticated users can read and mark only their own rows.

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references auth.users(id) on delete cascade,
  actor_id uuid references auth.users(id) on delete set null,
  request_id uuid references public.swap_requests(id) on delete cascade,
  type text not null check (type in ('swap_request','swap_accepted','swap_declined','new_message')),
  title text not null,
  body text not null,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists notifications_recipient_created_idx
  on public.notifications (recipient_id, created_at desc);
create index if not exists notifications_unread_idx
  on public.notifications (recipient_id, created_at desc)
  where is_read = false;

alter table public.notifications enable row level security;

revoke all on table public.notifications from public, anon, authenticated;
grant select on table public.notifications to authenticated;
grant update (is_read) on table public.notifications to authenticated;

drop policy if exists "Members can view their own notifications" on public.notifications;
create policy "Members can view their own notifications"
  on public.notifications for select to authenticated
  using ((select auth.uid()) = recipient_id);

drop policy if exists "Members can mark their own notifications read" on public.notifications;
create policy "Members can mark their own notifications read"
  on public.notifications for update to authenticated
  using ((select auth.uid()) = recipient_id)
  with check ((select auth.uid()) = recipient_id);

create or replace function public.notify_swap_request_events()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor_name text;
begin
  if tg_op = 'INSERT' and new.status = 'pending' then
    select coalesce(nullif(display_name, ''), 'A member')
      into v_actor_name from public.profiles where id = new.sender_id;

    insert into public.notifications
      (recipient_id, actor_id, request_id, type, title, body)
    values
      (new.recipient_id, new.sender_id, new.id, 'swap_request',
       'New swap request',
       coalesce(v_actor_name, 'A member') || ' wants to swap ' || new.skill || ' with you.');
  elsif tg_op = 'UPDATE'
        and old.status = 'pending'
        and new.status in ('accepted', 'declined') then
    select coalesce(nullif(display_name, ''), 'A member')
      into v_actor_name from public.profiles where id = new.recipient_id;

    insert into public.notifications
      (recipient_id, actor_id, request_id, type, title, body)
    values
      (new.sender_id, new.recipient_id, new.id,
       case when new.status = 'accepted' then 'swap_accepted' else 'swap_declined' end,
       case when new.status = 'accepted' then 'Swap request accepted' else 'Swap request declined' end,
       coalesce(v_actor_name, 'A member') ||
       case when new.status = 'accepted' then ' accepted your ' else ' declined your ' end ||
       new.skill || ' swap request.');
  end if;

  return new;
end;
$$;

create or replace function public.notify_mission_room_message()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_sender_id uuid;
  v_recipient_id uuid;
  v_skill text;
  v_other_user_id uuid;
  v_actor_name text;
begin
  select sender_id, recipient_id, skill
    into v_sender_id, v_recipient_id, v_skill
    from public.swap_requests
    where id = new.request_id
      and status = 'accepted'
      and (new.sender_id = sender_id or new.sender_id = recipient_id);

  if not found then
    return new;
  end if;

  v_other_user_id := case
    when new.sender_id = v_sender_id then v_recipient_id
    else v_sender_id
  end;

  select coalesce(nullif(display_name, ''), 'A member')
    into v_actor_name from public.profiles where id = new.sender_id;

  insert into public.notifications
    (recipient_id, actor_id, request_id, type, title, body)
  values
    (v_other_user_id, new.sender_id, new.request_id, 'new_message',
     'New Mission Room message',
     coalesce(v_actor_name, 'A member') || ' sent you a message in your ' || v_skill || ' room.');

  return new;
end;
$$;

revoke all on function public.notify_swap_request_events() from public, anon, authenticated;
revoke all on function public.notify_mission_room_message() from public, anon, authenticated;

drop trigger if exists notify_on_swap_request_change on public.swap_requests;
create trigger notify_on_swap_request_change
  after insert or update of status on public.swap_requests
  for each row execute function public.notify_swap_request_events();

drop trigger if exists notify_on_mission_room_message on public.swap_messages;
create trigger notify_on_mission_room_message
  after insert on public.swap_messages
  for each row execute function public.notify_mission_room_message();

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'notifications'
  ) then
    execute 'alter publication supabase_realtime add table public.notifications';
  end if;
end;
$$;
