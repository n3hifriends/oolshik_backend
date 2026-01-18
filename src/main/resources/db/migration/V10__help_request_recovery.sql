ALTER TABLE public.help_request
    ADD COLUMN IF NOT EXISTS assignment_expires_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS cancelled_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS cancelled_by uuid,
    ADD COLUMN IF NOT EXISTS cancel_reason_code character varying(32),
    ADD COLUMN IF NOT EXISTS cancel_reason_text text,
    ADD COLUMN IF NOT EXISTS released_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS reassigned_count integer DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS released_count integer DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS last_state_change_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS last_state_change_reason character varying(64);

CREATE TABLE IF NOT EXISTS public.help_request_event (
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    event_type character varying(32) NOT NULL,
    actor_user_id uuid,
    actor_role character varying(16) NOT NULL,
    reason_code character varying(32),
    reason_text text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    metadata jsonb,
    CONSTRAINT help_request_event_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_help_request_event_request_id_created_at
    ON public.help_request_event (request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_help_request_event_type_created_at
    ON public.help_request_event (event_type, created_at DESC);
