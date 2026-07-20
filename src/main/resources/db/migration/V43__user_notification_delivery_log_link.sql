ALTER TABLE public.user_notification
    ADD COLUMN IF NOT EXISTS delivery_log_id uuid;

ALTER TABLE public.user_notification
    ADD CONSTRAINT user_notification_delivery_log_fk
    FOREIGN KEY (delivery_log_id) REFERENCES public.notification_delivery_log (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_notification_delivery_log
    ON public.user_notification (delivery_log_id)
    WHERE delivery_log_id IS NOT NULL;
