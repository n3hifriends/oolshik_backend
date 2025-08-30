
-- V2__phone_reveal_event.sql
CREATE TABLE phone_reveal_event (
                                    id UUID PRIMARY KEY,
                                    requester_user_id UUID NOT NULL,   -- 👈 Add this column
                                    target_user_id UUID NOT NULL,
                                    phone_number VARCHAR(20) NOT NULL,
                                    revealed_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
                                    reveal_count INT DEFAULT 0
);

-- Optional: index for analytics
CREATE INDEX idx_phone_reveal_event_requester ON phone_reveal_event(requester_user_id);
CREATE INDEX idx_phone_reveal_event_target ON phone_reveal_event(target_user_id);