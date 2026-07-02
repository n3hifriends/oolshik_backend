ALTER TABLE app_user
    ADD COLUMN blocked        BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN blocked_at     TIMESTAMPTZ,
    ADD COLUMN blocked_reason VARCHAR(512),
    ADD COLUMN blocked_by     UUID REFERENCES app_user(id);
