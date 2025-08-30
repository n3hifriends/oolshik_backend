-- Ensure UUID generator exists (Postgres)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Moderation reports table
CREATE TABLE IF NOT EXISTS report_event (
                                            id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id   UUID NOT NULL REFERENCES app_user(id),
    target_user_id     UUID NULL REFERENCES app_user(id),
    help_request_id    UUID NULL REFERENCES help_request(id),
    reason             TEXT NOT NULL CHECK (reason IN ('SPAM','INAPPROPRIATE','UNSAFE','OTHER')),
    details            TEXT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_report_event_help_request_id ON report_event(help_request_id);
CREATE INDEX IF NOT EXISTS idx_report_event_target_user_id ON report_event(target_user_id);
CREATE INDEX IF NOT EXISTS idx_report_event_created_at ON report_event(created_at DESC);