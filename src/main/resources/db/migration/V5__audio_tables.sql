-- V30__audio_tables.sql
CREATE TABLE IF NOT EXISTS audio_files (
    id UUID PRIMARY KEY,
    owner_user_id VARCHAR(100) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    duration_ms BIGINT NULL,
    sample_rate INTEGER NULL,
    request_id VARCHAR(100) NULL
);