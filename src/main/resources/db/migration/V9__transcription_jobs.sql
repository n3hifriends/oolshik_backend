-- Transcription jobs for async STT processing
CREATE TABLE IF NOT EXISTS transcription_job (
    job_id           UUID PRIMARY KEY,
    task_id          UUID NOT NULL,
    audio_url        VARCHAR(500) NOT NULL,
    status           VARCHAR(32) NOT NULL,
    language_hint    VARCHAR(32),
    detected_language VARCHAR(32),
    transcript_text  TEXT,
    confidence       NUMERIC(5,4),
    engine           VARCHAR(64) NOT NULL,
    model_version    VARCHAR(64) NOT NULL,
    attempt_count    INTEGER NOT NULL DEFAULT 0,
    last_error_code  VARCHAR(64),
    last_error_message TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT transcription_job_task_id_uniq UNIQUE (task_id),
    CONSTRAINT transcription_job_task_fk FOREIGN KEY (task_id) REFERENCES help_request(id)
);

CREATE INDEX IF NOT EXISTS idx_transcription_job_status ON transcription_job(status);
CREATE INDEX IF NOT EXISTS idx_transcription_job_task_id ON transcription_job(task_id);
