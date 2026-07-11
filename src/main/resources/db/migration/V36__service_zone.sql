CREATE TABLE public.service_zone (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(128)     NOT NULL,
    type          VARCHAR(16)      NOT NULL,
    active        BOOLEAN          NOT NULL DEFAULT false,
    center_lat    DOUBLE PRECISION,
    center_lng    DOUBLE PRECISION,
    radius_km     DOUBLE PRECISION,
    boundary      TEXT,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_zone_active ON public.service_zone (active);
