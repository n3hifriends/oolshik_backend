CREATE TABLE IF NOT EXISTS public.user_notification (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    broadcast_id uuid,
    title varchar(100) NOT NULL,
    body text NOT NULL,
    read_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT user_notification_pkey PRIMARY KEY (id),
    CONSTRAINT user_notification_user_fk FOREIGN KEY (user_id)
        REFERENCES public.app_user (id) ON DELETE CASCADE,
    CONSTRAINT user_notification_broadcast_fk FOREIGN KEY (broadcast_id)
        REFERENCES public.admin_broadcast (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_user_notification_user_created
    ON public.user_notification (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notification_user_unread
    ON public.user_notification (user_id)
    WHERE read_at IS NULL;
