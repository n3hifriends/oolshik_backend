ALTER TABLE public.phone_reveal_event
    ADD COLUMN IF NOT EXISTS help_request_id uuid NULL,
    ADD COLUMN IF NOT EXISTS viewer_role varchar(20) NULL,
    ADD COLUMN IF NOT EXISTS target_role varchar(20) NULL,
    ADD COLUMN IF NOT EXISTS reveal_source varchar(40) NULL,
    ADD COLUMN IF NOT EXISTS masked_phone varchar(32) NULL;

CREATE INDEX IF NOT EXISTS idx_phone_reveal_event_help_request_id
    ON public.phone_reveal_event (help_request_id);
