ALTER TABLE public.notification_outbox
    ADD COLUMN IF NOT EXISTS acknowledged_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS acknowledged_by uuid,
    ADD COLUMN IF NOT EXISTS resolution_note text;

CREATE INDEX IF NOT EXISTS idx_notification_outbox_unresolved_failures
    ON public.notification_outbox (status)
    WHERE status = 'FAILED'
       OR (status = 'DEAD' AND acknowledged_at IS NULL);
