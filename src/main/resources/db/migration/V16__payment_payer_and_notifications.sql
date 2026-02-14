ALTER TABLE payment_requests
    ADD COLUMN IF NOT EXISTS requester_user UUID,
    ADD COLUMN IF NOT EXISTS helper_user UUID,
    ADD COLUMN IF NOT EXISTS payer_user UUID,
    ADD COLUMN IF NOT EXISTS payer_role VARCHAR(16),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW();

UPDATE payment_requests p
SET requester_user = h.requester_id,
    helper_user = h.helper_id
FROM help_request h
WHERE p.task_id = h.id
  AND (p.requester_user IS NULL OR p.helper_user IS NULL);

UPDATE payment_requests
SET payer_user = scanned_by_user
WHERE payer_user IS NULL;

UPDATE payment_requests
SET payer_role = 'HELPER'
WHERE payer_role IS NULL OR payer_role = '';

ALTER TABLE payment_requests
    ALTER COLUMN payer_user SET NOT NULL,
    ALTER COLUMN payer_role SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_payment_requests_payer_role'
    ) THEN
        ALTER TABLE payment_requests
            ADD CONSTRAINT ck_payment_requests_payer_role
            CHECK (payer_role IN ('REQUESTER', 'HELPER'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_payment_requests_payer_status
    ON payment_requests(payer_user, status);

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY created_at DESC, id DESC) AS rn
    FROM payment_requests
    WHERE status IN ('PENDING', 'INITIATED')
)
UPDATE payment_requests p
SET status = 'EXPIRED'
FROM ranked r
WHERE p.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_requests_task_active
    ON payment_requests(task_id)
    WHERE status IN ('PENDING', 'INITIATED');
