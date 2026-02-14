ALTER TABLE help_request
    ADD COLUMN IF NOT EXISTS offer_amount NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS offer_currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS offer_updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS offer_last_notified_amount NUMERIC(12,2);

CREATE TABLE IF NOT EXISTS help_request_offer_events (
    id UUID PRIMARY KEY,
    help_request_id UUID NOT NULL REFERENCES help_request(id) ON DELETE CASCADE,
    old_amount NUMERIC(12,2),
    new_amount NUMERIC(12,2),
    actor_user_id UUID NOT NULL REFERENCES app_user(id),
    event_ts TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    source VARCHAR(32) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_help_request_offer_events_request_ts
    ON help_request_offer_events (help_request_id, event_ts DESC);
