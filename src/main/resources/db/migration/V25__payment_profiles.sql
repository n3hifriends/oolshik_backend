CREATE TABLE IF NOT EXISTS payment_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    upi_id VARCHAR(160) NOT NULL,
    payee_label VARCHAR(120),
    source_type VARCHAR(16) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_profiles_user_id
    ON payment_profiles(user_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_profiles_active_user
    ON payment_profiles(user_id)
    WHERE is_active = TRUE;

ALTER TABLE payment_requests
    ADD COLUMN IF NOT EXISTS payment_mode VARCHAR(32) NOT NULL DEFAULT 'MERCHANT_QR';

ALTER TABLE payment_requests
    ADD COLUMN IF NOT EXISTS payment_profile_user UUID REFERENCES app_user(id);

CREATE INDEX IF NOT EXISTS idx_payment_requests_payment_profile_user
    ON payment_requests(payment_profile_user);
