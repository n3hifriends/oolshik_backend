ALTER TABLE user_notification
    ADD COLUMN event_type VARCHAR(64),
    ADD COLUMN task_id UUID,
    ADD COLUMN payment_request_id UUID,
    ADD COLUMN route VARCHAR(32);
