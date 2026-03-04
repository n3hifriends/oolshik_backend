CREATE TABLE IF NOT EXISTS public.app_config (
    config_key VARCHAR(128) PRIMARY KEY,
    config_value VARCHAR(256) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

INSERT INTO public.app_config (config_key, config_value)
VALUES ('MAX_ACTIVE_REQUESTS_PER_REQUESTER', '2')
ON CONFLICT (config_key) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_help_request_requester_status_created_desc
    ON public.help_request (requester_id, status, created_at DESC);
