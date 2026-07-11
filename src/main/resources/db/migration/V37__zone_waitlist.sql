CREATE TABLE public.zone_waitlist (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID             NOT NULL REFERENCES public.app_user (id),
    approx_lat  DOUBLE PRECISION,
    approx_lng  DOUBLE PRECISION,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uq_zone_waitlist_user UNIQUE (user_id)
);
