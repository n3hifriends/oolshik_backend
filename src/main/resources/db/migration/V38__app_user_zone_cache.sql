ALTER TABLE public.app_user
    ADD COLUMN zone_confirmed      BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN confirmed_zone_id   UUID        REFERENCES public.service_zone (id),
    ADD COLUMN zone_confirmed_at   TIMESTAMPTZ;
