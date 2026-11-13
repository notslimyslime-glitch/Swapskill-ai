-- Run this once in Supabase Dashboard -> SQL Editor -> New query.
-- Only the intended recipient can answer a pending request.
-- This function changes only the status column. No direct UPDATE policy is added.

create or replace function public.respond_to_swap_request(
  p_request_id uuid,
  p_status text
)
returns table(id uuid, status text)
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  if auth.uid() is null then
    raise exception 'You must be signed in.';
  end if;

  if p_status not in ('accepted', 'declined') then
    raise exception 'Status must be accepted or declined.';
  end if;

  return query
  update public.swap_requests as sr
     set status = p_status
   where sr.id = p_request_id
     and sr.recipient_id = auth.uid()
     and sr.status = 'pending'
  returning sr.id, sr.status;

  if not found then
    raise exception 'Request not found, already answered, or you are not its recipient.';
  end if;
end;
$function$;

revoke all on function public.respond_to_swap_request(uuid, text) from public;
grant execute on function public.respond_to_swap_request(uuid, text) to authenticated;
