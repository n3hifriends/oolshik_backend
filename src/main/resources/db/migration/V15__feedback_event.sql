-- Feedback events (MVP)
CREATE TABLE public.feedback_event (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    feedback_type text NOT NULL,
    context_type text NOT NULL,
    context_id uuid,
    rating smallint,
    tags jsonb,
    message text,
    locale character varying(16),
    app_version character varying(32),
    os character varying(32),
    device_model character varying(64),
    idempotency_key character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    retention_until timestamp with time zone NOT NULL,
    CONSTRAINT feedback_event_type_check CHECK ((feedback_type = ANY (ARRAY['BUG'::text, 'FEATURE'::text, 'CSAT'::text, 'SAFETY'::text, 'OTHER'::text]))),
    CONSTRAINT feedback_event_context_check CHECK ((context_type = ANY (ARRAY['APP'::text, 'TASK'::text, 'SCREEN'::text]))),
    CONSTRAINT feedback_event_rating_check CHECK ((rating IS NULL OR (rating >= 1 AND rating <= 5)))
);

ALTER TABLE ONLY public.feedback_event
    ADD CONSTRAINT feedback_event_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.feedback_event
    ADD CONSTRAINT feedback_event_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id);

CREATE UNIQUE INDEX idx_feedback_event_user_idempotency
    ON public.feedback_event USING btree (user_id, idempotency_key);

CREATE INDEX idx_feedback_event_user_created_at
    ON public.feedback_event USING btree (user_id, created_at DESC);

CREATE INDEX idx_feedback_event_type_created_at
    ON public.feedback_event USING btree (feedback_type, created_at DESC);

CREATE INDEX idx_feedback_event_context
    ON public.feedback_event USING btree (context_type, context_id);

CREATE INDEX idx_feedback_event_retention
    ON public.feedback_event USING btree (retention_until);
