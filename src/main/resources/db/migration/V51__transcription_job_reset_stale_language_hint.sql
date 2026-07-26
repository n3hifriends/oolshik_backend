-- STT language hint used to be derived from the user's UI display-language preference, not the
-- language actually spoken in the recording, and defaulted to English for most users. Non-terminal
-- jobs created before this fix carry that stale hint. Correct them once to "auto" so they go through
-- real language auto-detection on their next retry/republish. COMPLETED and PROCESSING jobs are left
-- untouched: COMPLETED jobs would require a separate reprocessing decision (out of scope), and
-- PROCESSING jobs are already mid-flight with whatever hint was dispatched.
UPDATE transcription_job
SET language_hint = 'auto'
WHERE status IN ('PENDING', 'FAILED');
