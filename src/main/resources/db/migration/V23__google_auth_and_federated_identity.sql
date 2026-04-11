ALTER TABLE app_user
    ALTER COLUMN phone_number DROP NOT NULL;

ALTER TABLE app_user
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE federated_identity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(191) NOT NULL,
    email VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uq_federated_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_federated_identity_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_federated_identity_user_id ON federated_identity (user_id);
