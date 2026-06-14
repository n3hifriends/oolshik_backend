ALTER TABLE otp_audit_log
    ADD COLUMN IF NOT EXISTS phone_hash varchar(64);

CREATE INDEX IF NOT EXISTS idx_otp_audit_log_phone_hash_created_at
    ON otp_audit_log (phone_hash, created_at DESC)
    WHERE phone_hash IS NOT NULL;
