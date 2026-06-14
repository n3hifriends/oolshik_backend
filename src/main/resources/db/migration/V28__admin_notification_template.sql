CREATE TABLE IF NOT EXISTS public.admin_notification_template (
    id uuid NOT NULL,
    name varchar(100) NOT NULL,
    title varchar(100) NOT NULL,
    body text NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT admin_notification_template_pkey PRIMARY KEY (id),
    CONSTRAINT admin_notification_template_name_unique UNIQUE (name),
    CONSTRAINT admin_notification_template_created_by_fk FOREIGN KEY (created_by)
        REFERENCES public.app_user (id) ON DELETE SET NULL
);
