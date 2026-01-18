ALTER TABLE public.help_request
    ADD COLUMN IF NOT EXISTS radius_stage integer DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS next_escalation_at timestamp with time zone;

CREATE TABLE IF NOT EXISTS public.helper_location (
    helper_id uuid NOT NULL,
    location public.geography(Point,4326) NOT NULL,
    last_seen_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT helper_location_pkey PRIMARY KEY (helper_id)
);

CREATE INDEX IF NOT EXISTS idx_helper_location_geo
    ON public.helper_location USING gist (location);

CREATE INDEX IF NOT EXISTS idx_helper_location_last_seen
    ON public.helper_location (last_seen_at DESC);

CREATE TABLE IF NOT EXISTS public.help_request_notify (
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    helper_id uuid NOT NULL,
    wave integer NOT NULL,
    notified_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT help_request_notify_pkey PRIMARY KEY (id),
    CONSTRAINT help_request_notify_unique UNIQUE (request_id, helper_id, wave)
);

CREATE INDEX IF NOT EXISTS idx_help_request_notify_request_id
    ON public.help_request_notify (request_id);

CREATE INDEX IF NOT EXISTS idx_help_request_notify_helper_id
    ON public.help_request_notify (helper_id);
