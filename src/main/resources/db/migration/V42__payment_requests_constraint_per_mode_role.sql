-- Narrow the payment_requests uniqueness constraint from per-task to per (task, mode, role).
--
-- The original constraint (V16) was created before payment_mode existed (V25).
-- Three distinct modes now exist (MERCHANT_QR, PAY_HELPER_DIRECT, PAY_REQUESTER_DIRECT),
-- each with an independent payer role, so they may legitimately co-exist for the same task.

-- Step 1: Expire older duplicates per (task_id, payment_mode, payer_role) so the new
-- index can be created cleanly. In practice this should be a no-op on a healthy DB.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY task_id, payment_mode, payer_role
               ORDER BY created_at DESC, id DESC
           ) AS rn
    FROM payment_requests
    WHERE status IN ('PENDING', 'INITIATED')
)
UPDATE payment_requests p
SET status = 'EXPIRED'
FROM ranked r
WHERE p.id = r.id
  AND r.rn > 1;

-- Step 2: Drop the old task-level constraint.
DROP INDEX IF EXISTS ux_payment_requests_task_active;

-- Step 3: Create the new constraint scoped to (task_id, payment_mode, payer_role).
CREATE UNIQUE INDEX ux_payment_requests_task_mode_role_active
    ON payment_requests(task_id, payment_mode, payer_role)
    WHERE status IN ('PENDING', 'INITIATED');
