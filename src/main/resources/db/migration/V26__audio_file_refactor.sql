ALTER TABLE public.audio_files
    ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(255),
    ADD COLUMN IF NOT EXISTS storage_region VARCHAR(64),
    ADD COLUMN IF NOT EXISTS storage_endpoint VARCHAR(255),
    ADD COLUMN IF NOT EXISTS storage_path_style_access_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE public.audio_files
SET storage_provider = 'LOCAL'
WHERE storage_provider IS NULL
  AND storage_key IS NOT NULL
  AND (storage_key LIKE 'store/%' OR storage_key LIKE 'tmp/%');

ALTER TABLE public.help_request
    ADD COLUMN IF NOT EXISTS audio_file_id UUID REFERENCES public.audio_files(id);

UPDATE public.help_request
SET audio_file_id = substring(voice_url from '/api/(?:public/)?media/audio/([0-9a-fA-F-]{36})/stream')::uuid
WHERE audio_file_id IS NULL
  AND voice_url IS NOT NULL
  AND voice_url ~ '/api/(public/)?media/audio/[0-9a-fA-F-]{36}/stream';

ALTER TABLE public.transcription_job
    ADD COLUMN IF NOT EXISTS audio_file_id UUID REFERENCES public.audio_files(id);

UPDATE public.transcription_job tj
SET audio_file_id = h.audio_file_id
FROM public.help_request h
WHERE tj.audio_file_id IS NULL
  AND tj.task_id = h.id
  AND h.audio_file_id IS NOT NULL;

ALTER TABLE public.transcription_job
    ALTER COLUMN audio_url DROP NOT NULL;
