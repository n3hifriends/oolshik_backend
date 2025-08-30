-- V3__phone_reveal_event_fix.sql
-- 1) add help_request_id
ALTER TABLE phone_reveal_event ADD COLUMN IF NOT EXISTS help_request_id UUID;

-- 2) backfill help_request_id from the incorrect requester_user_id that currently holds help_request.id
--    and fix requester_user_id to the real user id
UPDATE phone_reveal_event pre
SET help_request_id = pre.requester_user_id
WHERE pre.help_request_id IS NULL;

UPDATE phone_reveal_event pre
SET requester_user_id = hr.requester_id
    FROM help_request hr
WHERE pre.help_request_id = hr.id
  AND pre.requester_user_id <> hr.requester_id;

-- 3) defaults
ALTER TABLE phone_reveal_event
    ALTER COLUMN reveal_count SET DEFAULT 0;

-- 4) uniqueness per (help_request_id, target_user_id)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public'
      AND indexname = 'uk_reveal_req_target'
  ) THEN
CREATE UNIQUE INDEX uk_reveal_req_target
    ON phone_reveal_event (help_request_id, target_user_id);
END IF;
END$$;