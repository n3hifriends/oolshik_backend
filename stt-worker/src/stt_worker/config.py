from __future__ import annotations

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", case_sensitive=False)

    log_level: str = Field("INFO", alias="LOG_LEVEL")

    kafka_bootstrap_servers: str = Field("localhost:9092", alias="KAFKA_BOOTSTRAP_SERVERS")
    kafka_group_id: str = Field("stt-worker", alias="KAFKA_GROUP_ID")
    stt_jobs_topic: str = Field("stt.jobs", alias="STT_JOBS_TOPIC")
    stt_results_topic: str = Field("stt.results", alias="STT_RESULTS_TOPIC")
    stt_dlq_topic: str = Field("stt.jobs.dlq", alias="STT_DLQ_TOPIC")

    worker_concurrency: int = Field(1, alias="WORKER_CONCURRENCY")
    queue_maxsize: int = Field(4, alias="WORKER_QUEUE_MAX")

    max_attempts: int = Field(3, alias="MAX_ATTEMPTS")
    retry_backoff_base_ms: int = Field(500, alias="RETRY_BACKOFF_BASE_MS")
    retry_backoff_max_ms: int = Field(5000, alias="RETRY_BACKOFF_MAX_MS")

    audio_download_timeout_sec: int = Field(30, alias="AUDIO_DOWNLOAD_TIMEOUT")
    transcribe_timeout_sec: int = Field(120, alias="TRANSCRIBE_TIMEOUT")
    max_audio_bytes: int = Field(25 * 1024 * 1024, alias="MAX_AUDIO_BYTES")
    max_audio_duration_sec: int = Field(600, alias="MAX_AUDIO_DURATION_SEC")

    model_size: str = Field("small", alias="MODEL_SIZE")
    device: str = Field("cpu", alias="DEVICE")
    compute_type: str | None = Field(None, alias="COMPUTE_TYPE")

    metrics_port: int = Field(9108, alias="METRICS_PORT")
    health_port: int = Field(8081, alias="HEALTH_PORT")

    otel_exporter_otlp_endpoint: str | None = Field(None, alias="OTEL_EXPORTER_OTLP_ENDPOINT")

    tmp_dir: str = Field("/tmp/stt", alias="TMP_DIR")


settings = Settings()
