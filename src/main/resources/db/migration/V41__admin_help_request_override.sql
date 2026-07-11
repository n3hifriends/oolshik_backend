ALTER TABLE help_request
    ADD COLUMN IF NOT EXISTS admin_override_reason TEXT;
