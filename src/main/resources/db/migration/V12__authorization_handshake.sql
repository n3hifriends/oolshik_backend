ALTER TABLE public.help_request
    ADD COLUMN pending_helper_id uuid,
    ADD COLUMN pending_auth_expires_at timestamp with time zone,
    ADD COLUMN accepted_at timestamp with time zone,
    ADD COLUMN authorized_at timestamp with time zone,
    ADD COLUMN authorized_by uuid,
    ADD COLUMN rejected_at timestamp with time zone,
    ADD COLUMN rejected_by uuid,
    ADD COLUMN reject_reason_code character varying(32),
    ADD COLUMN reject_reason_text text,
    ADD COLUMN auth_timeout_count integer NOT NULL DEFAULT 0;
