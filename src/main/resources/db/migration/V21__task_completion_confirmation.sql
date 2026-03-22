ALTER TABLE public.help_request
    ADD COLUMN work_done_at timestamptz NULL,
    ADD COLUMN completed_at timestamptz NULL,
    ADD COLUMN completion_confirmation_expires_at timestamptz NULL,
    ADD COLUMN issue_reported_at timestamptz NULL,
    ADD COLUMN issue_reason_code varchar(32) NULL,
    ADD COLUMN issue_reason_text text NULL,
    ADD COLUMN completion_mode varchar(32) NULL,
    ADD COLUMN completed_by uuid NULL,
    ADD COLUMN reminder_50_sent boolean NOT NULL DEFAULT false,
    ADD COLUMN reminder_80_sent boolean NOT NULL DEFAULT false;

CREATE INDEX idx_help_request_completion_confirmation
    ON public.help_request (status, completion_confirmation_expires_at);

CREATE INDEX idx_help_request_reminder_50
    ON public.help_request (status, reminder_50_sent, work_done_at);

CREATE INDEX idx_help_request_reminder_80
    ON public.help_request (status, reminder_80_sent, work_done_at);
