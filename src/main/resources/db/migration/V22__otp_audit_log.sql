ALTER TABLE otp_code
    ADD COLUMN provider VARCHAR(32);

CREATE TABLE otp_audit_log (
    id UUID PRIMARY KEY,
    masked_phone VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    detail VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_audit_log_created_at ON otp_audit_log (created_at);
