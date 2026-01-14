from __future__ import annotations

from prometheus_client import Counter, Histogram, start_http_server

STT_JOBS_TOTAL = Counter("stt_jobs_total", "Total STT jobs", ["status"])
STT_FAILURES_TOTAL = Counter("stt_failures_total", "Total STT failures", ["code"])
STT_DOWNLOAD_SECONDS = Histogram("stt_download_seconds", "Download duration seconds")
STT_TRANSCRIBE_SECONDS = Histogram("stt_transcribe_seconds", "Transcribe duration seconds")
STT_PROCESSING_SECONDS = Histogram("stt_processing_seconds", "End-to-end processing seconds")


def start_metrics(port: int) -> None:
    start_http_server(port)
