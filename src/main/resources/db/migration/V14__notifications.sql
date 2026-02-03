CREATE TABLE IF NOT EXISTS public.user_device (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    provider varchar(32) NOT NULL DEFAULT 'EXPO',
    platform varchar(16),
    token varchar(512) NOT NULL,
    token_hash varchar(128) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    last_seen_at timestamp with time zone NOT NULL DEFAULT now(),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT user_device_pkey PRIMARY KEY (id),
    CONSTRAINT user_device_token_hash_unique UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_user_device_user_active
    ON public.user_device (user_id, is_active);

CREATE TABLE IF NOT EXISTS public.notification_outbox (
    id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    aggregate_id uuid NOT NULL,
    payload_json text NOT NULL,
    status varchar(16) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone NOT NULL,
    last_error text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT notification_outbox_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_status_next
    ON public.notification_outbox (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS public.notification_delivery_log (
    id uuid NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    event_id uuid NOT NULL,
    recipient_user_id uuid NOT NULL,
    provider varchar(32) NOT NULL DEFAULT 'EXPO',
    status varchar(16) NOT NULL,
    last_error text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT notification_delivery_log_pkey PRIMARY KEY (id),
    CONSTRAINT notification_delivery_log_idempotency_key_unique UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_recipient
    ON public.notification_delivery_log (recipient_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.help_request_candidate (
    id uuid NOT NULL,
    help_request_id uuid NOT NULL,
    helper_user_id uuid NOT NULL,
    state varchar(16) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT help_request_candidate_pkey PRIMARY KEY (id),
    CONSTRAINT help_request_candidate_unique UNIQUE (help_request_id, helper_user_id)
);

CREATE INDEX IF NOT EXISTS idx_help_request_candidate_request_state
    ON public.help_request_candidate (help_request_id, state);

CREATE TABLE IF NOT EXISTS public.help_request_notification_audience (
    id uuid NOT NULL,
    help_request_id uuid NOT NULL,
    user_id uuid NOT NULL,
    notified_for varchar(32) NOT NULL,
    radius_meters integer,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT help_request_notification_audience_pkey PRIMARY KEY (id),
    CONSTRAINT help_request_notification_audience_unique UNIQUE (help_request_id, user_id, notified_for, radius_meters)
);

CREATE INDEX IF NOT EXISTS idx_help_request_notification_audience_request
    ON public.help_request_notification_audience (help_request_id);
