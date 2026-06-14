CREATE TABLE IF NOT EXISTS public.admin_broadcast_delivery (
    id uuid NOT NULL,
    broadcast_id uuid NOT NULL,
    user_id uuid NOT NULL,
    channel varchar(10) NOT NULL,
    status varchar(10) NOT NULL,
    error text,
    sent_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT admin_broadcast_delivery_pkey PRIMARY KEY (id),
    CONSTRAINT admin_broadcast_delivery_broadcast_fk FOREIGN KEY (broadcast_id)
        REFERENCES public.admin_broadcast (id) ON DELETE CASCADE,
    CONSTRAINT admin_broadcast_delivery_user_fk FOREIGN KEY (user_id)
        REFERENCES public.app_user (id) ON DELETE CASCADE,
    CONSTRAINT admin_broadcast_delivery_unique UNIQUE (broadcast_id, user_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_admin_broadcast_delivery_broadcast
    ON public.admin_broadcast_delivery (broadcast_id);

CREATE INDEX IF NOT EXISTS idx_admin_broadcast_delivery_broadcast_channel_status
    ON public.admin_broadcast_delivery (broadcast_id, channel, status);
