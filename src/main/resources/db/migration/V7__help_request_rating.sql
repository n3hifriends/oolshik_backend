ALTER TABLE help_request
    ADD COLUMN IF NOT EXISTS rating_value NUMERIC(2,1) CHECK (rating_value >= 0 AND rating_value <= 5),
    ADD COLUMN IF NOT EXISTS rated_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS rated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_help_request_rating ON help_request (rating_value);