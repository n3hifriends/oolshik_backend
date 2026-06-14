ALTER TABLE public.report_event
    ADD COLUMN IF NOT EXISTS status varchar(24) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS priority varchar(24) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS assigned_admin_user_id uuid,
    ADD COLUMN IF NOT EXISTS resolution_note text,
    ADD COLUMN IF NOT EXISTS resolved_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();

ALTER TABLE ONLY public.report_event
    ADD CONSTRAINT report_event_status_check
    CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED'));

ALTER TABLE ONLY public.report_event
    ADD CONSTRAINT report_event_priority_check
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

ALTER TABLE ONLY public.report_event
    ADD CONSTRAINT report_event_assigned_admin_user_id_fkey
    FOREIGN KEY (assigned_admin_user_id) REFERENCES public.app_user(id);

CREATE INDEX IF NOT EXISTS idx_report_event_status_created_at
    ON public.report_event (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_report_event_reporter_user_id
    ON public.report_event (reporter_user_id);

CREATE INDEX IF NOT EXISTS idx_report_event_assigned_admin
    ON public.report_event (assigned_admin_user_id, status);

CREATE TABLE IF NOT EXISTS public.report_action (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    report_id uuid NOT NULL,
    admin_user_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    from_status varchar(24),
    to_status varchar(24),
    note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_action_pkey PRIMARY KEY (id),
    CONSTRAINT report_action_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.report_event(id),
    CONSTRAINT report_action_admin_user_id_fkey FOREIGN KEY (admin_user_id) REFERENCES public.app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_report_action_report_created_at
    ON public.report_action (report_id, created_at DESC);
