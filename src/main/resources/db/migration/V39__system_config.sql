CREATE TABLE public.system_config (
    key        VARCHAR(128) PRIMARY KEY,
    value      TEXT         NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by UUID         REFERENCES public.app_user (id)
);

INSERT INTO public.system_config (key, value, updated_at, updated_by)
VALUES ('zone.gate.enabled', 'true', now(), null)
ON CONFLICT (key) DO NOTHING;
