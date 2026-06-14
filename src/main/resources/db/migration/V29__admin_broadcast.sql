CREATE TABLE IF NOT EXISTS public.admin_broadcast (
    id uuid NOT NULL,
    template_id uuid,
    title varchar(100) NOT NULL,
    body text NOT NULL,
    target_type varchar(20) NOT NULL,
    target_value varchar(100),
    channels varchar(50) NOT NULL,
    status varchar(20) NOT NULL,
    total_recipients integer NOT NULL DEFAULT 0,
    push_sent integer NOT NULL DEFAULT 0,
    push_failed integer NOT NULL DEFAULT 0,
    sms_sent integer NOT NULL DEFAULT 0,
    sms_failed integer NOT NULL DEFAULT 0,
    in_app_created integer NOT NULL DEFAULT 0,
    created_by uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    processing_started_at timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT admin_broadcast_pkey PRIMARY KEY (id),
    CONSTRAINT admin_broadcast_template_fk FOREIGN KEY (template_id)
        REFERENCES public.admin_notification_template (id) ON DELETE SET NULL,
    CONSTRAINT admin_broadcast_created_by_fk FOREIGN KEY (created_by)
        REFERENCES public.app_user (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_broadcast_status_created_at
    ON public.admin_broadcast (status, created_at);
