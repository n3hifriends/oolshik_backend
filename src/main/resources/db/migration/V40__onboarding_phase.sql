-- Add onboarding_phase column to app_user.
-- New users default to FRESH via application-level @PrePersist; the column is nullable
-- so existing rows can be backfilled below without a two-step migration.

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS onboarding_phase VARCHAR(32);

-- Backfill existing users from their help_request history.
-- GRADUATED: requester has at least one COMPLETED request.
-- FIRST_ACTION: requester has any request, OR user was accepted as helper on any request.
-- Everyone else: FRESH.

UPDATE app_user u
SET onboarding_phase = CASE
    WHEN EXISTS (
        SELECT 1 FROM help_request hr
        WHERE hr.requester_id = u.id
          AND hr.status = 'COMPLETED'
    ) THEN 'GRADUATED'
    WHEN EXISTS (
        SELECT 1 FROM help_request hr
        WHERE hr.requester_id = u.id
           OR hr.helper_id = u.id
    ) THEN 'FIRST_ACTION'
    ELSE 'FRESH'
END
WHERE u.onboarding_phase IS NULL;
