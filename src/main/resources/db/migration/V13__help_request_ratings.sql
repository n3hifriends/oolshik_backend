CREATE TABLE public.help_request_rating (
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    rater_user_id uuid NOT NULL,
    target_user_id uuid NOT NULL,
    rating_value numeric(2,1) NOT NULL,
    rater_role character varying(16) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT help_request_rating_pkey PRIMARY KEY (id),
    CONSTRAINT help_request_rating_request_id_fkey FOREIGN KEY (request_id)
        REFERENCES public.help_request(id),
    CONSTRAINT help_request_rating_rater_user_id_fkey FOREIGN KEY (rater_user_id)
        REFERENCES public.app_user(id),
    CONSTRAINT help_request_rating_target_user_id_fkey FOREIGN KEY (target_user_id)
        REFERENCES public.app_user(id),
    CONSTRAINT help_request_rating_unique UNIQUE (request_id, rater_user_id),
    CONSTRAINT help_request_rating_value_check CHECK (
        rating_value >= (0)::numeric AND rating_value <= (5)::numeric
    )
);

CREATE INDEX idx_help_request_rating_target_user ON public.help_request_rating USING btree (target_user_id);
CREATE INDEX idx_help_request_rating_request_id ON public.help_request_rating USING btree (request_id);
