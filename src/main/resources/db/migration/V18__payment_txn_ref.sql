ALTER TABLE payment_requests
    ADD COLUMN IF NOT EXISTS txn_ref VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_payment_requests_txn_ref
    ON payment_requests(txn_ref);
