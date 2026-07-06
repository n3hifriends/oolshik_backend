ALTER TABLE public.report_event
  DROP CONSTRAINT IF EXISTS report_event_reason_check;

ALTER TABLE public.report_event
  ADD CONSTRAINT report_event_reason_check
  CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'UNSAFE', 'OTHER', 'CHILD_SAFETY'));
