CREATE INDEX IF NOT EXISTS idx_help_request_candidate_helper_state
    ON public.help_request_candidate (helper_user_id, state);
