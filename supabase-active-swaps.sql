-- Run once in Supabase Dashboard -> SQL Editor -> New query.
-- Adds mutual completion tracking for accepted skill swaps.

alter table public.swap_requests
  add column if not exists sender_completed boolean not null default false,
  add column if not exists recipient_completed boolean not null default false;

create or replace function public.mark_swap_complete(p_request_id uuid)
returns table(id uuid, sender_completed boolean, recipient_completed boolean)
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  if auth.uid() is null then
    raise exception 'You must be signed in.';
  end if;

  return query
  update public.swap_requests as sr
     set sender_completed = case
           when sr.sender_id = auth.uid() then true
           else sr.sender_completed
         end,
         recipient_completed = case
           when sr.recipient_id = auth.uid() then true
           else sr.recipient_completed
         end
   where sr.id = p_request_id
     and sr.status = 'accepted'
     and (sr.sender_id = auth.uid() or sr.recipient_id = auth.uid())
  returning sr.id, sr.sender_completed, sr.recipient_completed;

  if not found then
    raise exception 'Accepted swap not found, or you are not part of it.';
  end if;
end;
$function$;

revoke all on function public.mark_swap_complete(uuid) from public;
grant execute on function public.mark_swap_complete(uuid) to authenticated;
