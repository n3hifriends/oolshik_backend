-- Preserve the QR-scanned payee identity separately from the resolved payee
-- (payee_vpa/payee_name), which for REQUESTER-pays requests is overwritten with
-- the helper's saved Payment Profile. This lets the payer be offered a choice
-- between paying the helper's saved UPI ID or the UPI ID from the scanned QR.
ALTER TABLE payment_requests
    ADD COLUMN IF NOT EXISTS scanned_payee_vpa VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scanned_payee_name VARCHAR(255);
