from __future__ import annotations

import os
import queue
import signal
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from cachetools import TTLCache
from confluent_kafka import KafkaError, Message
from pydantic import ValidationError

from stt_worker.audio.http_fetcher import AudioDownloadError, HttpAudioFetcher
from stt_worker.config import Settings, settings
from stt_worker.dlq import build_dlq_message
from stt_worker.health import set_ready, start_health_server
from stt_worker.kafka_consumer import create_consumer, pause_consumer, resume_consumer
from stt_worker.kafka_producer import create_producer, flush_producer, produce_json
from stt_worker.logging import configure_logging, with_context
from stt_worker.metrics import (
    STT_DOWNLOAD_SECONDS,
    STT_FAILURES_TOTAL,
    STT_JOBS_TOTAL,
    STT_PROCESSING_SECONDS,
    STT_TRANSCRIBE_SECONDS,
    start_metrics,
)
from stt_worker.retry import ErrorInfo, backoff_ms, classify_error
from stt_worker.schema import JobMessage, ResultMessage, ResultStatus
from stt_worker.transcribe.engine import FasterWhisperEngine, TranscribeError
from stt_worker.validator import parse_job, safe_payload


@dataclass
class Outcome:
    msg: Message
    commit: bool


@dataclass
class WorkItem:
    msg: Message


def get_attempt(msg: Message) -> int:
    headers = msg.headers() or []
    for key, value in headers:
        if key != "attempt":
            continue
        try:
            if value is None:
                return 0
            if isinstance(value, (bytes, bytearray)):
                return int(value.decode("utf-8"))
            return int(value)
        except Exception:
            return 0
    return 0


def ffmpeg_convert(input_path: str, output_path: str) -> None:
    import subprocess

    cmd = [
        "ffmpeg",
        "-y",
        "-i",
        input_path,
        "-ac",
        "1",
        "-ar",
        "16000",
        output_path,
    ]
    try:
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except subprocess.CalledProcessError as exc:
        raise AudioDownloadError("UNSUPPORTED_FORMAT", "ffmpeg conversion failed", False) from exc


def wav_duration_seconds(path: str) -> float:
    import wave

    with wave.open(path, "rb") as wf:
        frames = wf.getnframes()
        rate = wf.getframerate()
        if rate == 0:
            return 0.0
        return frames / float(rate)


def build_result(
    job: JobMessage,
    status: ResultStatus,
    transcript_text: Optional[str],
    detected_language: Optional[str],
    confidence: Optional[float],
    engine: str,
    model_version: str,
    error: Optional[ErrorInfo],
) -> Dict[str, Any]:
    return ResultMessage(
        jobId=job.jobId,
        taskId=job.taskId,
        status=status,
        transcriptText=transcript_text,
        detectedLanguage=detected_language,
        confidence=confidence,
        engine=engine,
        modelVersion=model_version,
        errorCode=error.code if error else None,
        errorMessage=error.message if error else None,
        completedAt=datetime.now(timezone.utc),
        correlationId=job.correlationId,
    ).model_dump(mode="json")


def handle_failure(
    logger,
    settings: Settings,
    producer,
    msg: Message,
    job_payload: Optional[Dict[str, Any]],
    attempt: int,
    error: ErrorInfo,
    failure_stage: str,
) -> bool:
    if error.retryable and attempt < settings.max_attempts:
        delay_ms = backoff_ms(attempt + 1, settings.retry_backoff_base_ms, settings.retry_backoff_max_ms)
        logger.info(
            "Retrying job",
            extra={"stage": "retry", "jobId": (job_payload or {}).get("jobId")},
        )
        time.sleep(delay_ms / 1000.0)
        retry_payload = dict(job_payload or {})
        headers = [("attempt", str(attempt + 1))]
        produce_json(producer, settings.stt_jobs_topic, retry_payload.get("jobId", ""), retry_payload, headers=headers)
        flush_producer(producer)
        return True

    dlq = build_dlq_message(
        original_topic=msg.topic(),
        original_partition=msg.partition(),
        original_offset=msg.offset(),
        failure_stage=failure_stage,
        error=error,
        attempt=attempt,
        job_payload=job_payload,
    )
    produce_json(producer, settings.stt_dlq_topic, (job_payload or {}).get("jobId", ""), dlq)
    flush_producer(producer)

    if job_payload:
        try:
            job = JobMessage.model_validate(job_payload)
            result = build_result(
                job=job,
                status=ResultStatus.FAILED,
                transcript_text=None,
                detected_language=None,
                confidence=None,
                engine="faster-whisper",
                model_version=settings.model_size,
                error=error,
            )
            produce_json(producer, settings.stt_results_topic, job.jobId, result)
            flush_producer(producer)
        except ValidationError:
            pass

    STT_FAILURES_TOTAL.labels(error.code).inc()
    STT_JOBS_TOTAL.labels("failed").inc()
    return True


def process_message(
    settings: Settings,
    producer,
    engine: FasterWhisperEngine,
    fetcher: HttpAudioFetcher,
    completed_cache: TTLCache,
    msg: Message,
) -> bool:
    raw_payload, raw_error = safe_payload(msg.value())
    log = with_context(
        logger,
        jobId=(raw_payload or {}).get("jobId"),
        correlationId=(raw_payload or {}).get("correlationId"),
    )

    if raw_error:
        error = ErrorInfo(code="INVALID_PAYLOAD", message=raw_error, retryable=False)
        handle_failure(log, settings, producer, msg, None, get_attempt(msg), error, "VALIDATION")
        return True

    try:
        job = parse_job(msg.value())
    except ValidationError as exc:
        error = ErrorInfo(code="INVALID_PAYLOAD", message=str(exc), retryable=False)
        handle_failure(log, settings, producer, msg, raw_payload, get_attempt(msg), error, "VALIDATION")
        return True

    if job.jobId in completed_cache:
        log.info("Job already processed", extra={"stage": "dedupe"})
        return True

    start = time.perf_counter()
    attempt = get_attempt(msg)
    tmp_dir = settings.tmp_dir
    os.makedirs(tmp_dir, exist_ok=True)
    input_path = os.path.join(tmp_dir, f"{job.jobId}.input")
    wav_path = os.path.join(tmp_dir, f"{job.jobId}.wav")

    try:
        download_start = time.perf_counter()
        fetcher.fetch(
            str(job.audioUrl),
            dest_path=input_path,
            timeout_sec=settings.audio_download_timeout_sec,
            max_bytes=settings.max_audio_bytes,
        )
        STT_DOWNLOAD_SECONDS.observe(time.perf_counter() - download_start)

        ffmpeg_convert(input_path, wav_path)
        duration = wav_duration_seconds(wav_path)
        if duration > settings.max_audio_duration_sec:
            raise AudioDownloadError("AUDIO_TOO_LONG", f"Audio longer than {settings.max_audio_duration_sec}s", False)

        transcribe_start = time.perf_counter()
        result = engine.transcribe(wav_path, job.languageHint, settings.transcribe_timeout_sec)
        STT_TRANSCRIBE_SECONDS.observe(time.perf_counter() - transcribe_start)

        transcript_text = result.text or None
        detected_language = result.language
        confidence = result.language_probability

        result_payload = build_result(
            job=job,
            status=ResultStatus.COMPLETED,
            transcript_text=transcript_text,
            detected_language=detected_language,
            confidence=confidence,
            engine="faster-whisper",
            model_version=settings.model_size,
            error=None,
        )
        produce_json(producer, settings.stt_results_topic, job.jobId, result_payload)
        flush_producer(producer)
        completed_cache[job.jobId] = True
        STT_JOBS_TOTAL.labels("completed").inc()
        STT_PROCESSING_SECONDS.observe(time.perf_counter() - start)
        log.info(
            "Job completed",
            extra={"stage": "completed", "durationMs": int((time.perf_counter() - start) * 1000)},
        )
        return True
    except AudioDownloadError as exc:
        error = classify_error(exc)
        log.warning(
            "Download error",
            extra={"stage": "download", "jobId": job.jobId, "correlationId": job.correlationId},
        )
        return handle_failure(log, settings, producer, msg, raw_payload, attempt, error, "DOWNLOAD")
    except TranscribeError as exc:
        error = classify_error(exc)
        log.warning(
            "Transcribe error",
            extra={"stage": "transcribe", "jobId": job.jobId, "correlationId": job.correlationId},
        )
        return handle_failure(log, settings, producer, msg, raw_payload, attempt, error, "TRANSCRIBE")
    except Exception as exc:  # noqa: BLE001
        error = classify_error(exc)
        return handle_failure(log, settings, producer, msg, raw_payload, attempt, error, "PUBLISH")
    finally:
        for path in (input_path, wav_path):
            try:
                if os.path.exists(path):
                    os.remove(path)
            except Exception:
                pass


def worker_loop(
    settings: Settings,
    producer,
    engine: FasterWhisperEngine,
    fetcher: HttpAudioFetcher,
    completed_cache: TTLCache,
    work_q: "queue.Queue[WorkItem]",
    result_q: "queue.Queue[Outcome]",
    stop_event: threading.Event,
) -> None:
    while not stop_event.is_set():
        try:
            item = work_q.get(timeout=0.5)
        except queue.Empty:
            continue
        commit = process_message(settings, producer, engine, fetcher, completed_cache, item.msg)
        result_q.put(Outcome(msg=item.msg, commit=commit))
        work_q.task_done()


def consume_loop(settings: Settings) -> None:
    consumer = create_consumer(settings)
    producer = create_producer(settings)
    fetcher = HttpAudioFetcher()
    engine = FasterWhisperEngine(settings.model_size, settings.device, settings.compute_type)

    completed_cache: TTLCache = TTLCache(maxsize=10000, ttl=3600)
    work_q: "queue.Queue[WorkItem]" = queue.Queue(maxsize=settings.queue_maxsize)
    result_q: "queue.Queue[Outcome]" = queue.Queue()
    stop_event = threading.Event()

    def shutdown(*_: Any) -> None:
        stop_event.set()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    workers = []
    for _ in range(max(1, settings.worker_concurrency)):
        thread = threading.Thread(
            target=worker_loop,
            args=(settings, producer, engine, fetcher, completed_cache, work_q, result_q, stop_event),
            daemon=True,
        )
        thread.start()
        workers.append(thread)

    consumer.subscribe([settings.stt_jobs_topic])
    set_ready(True)

    paused = False
    while not stop_event.is_set():
        try:
            while True:
                outcome = result_q.get_nowait()
                if outcome.commit:
                    consumer.commit(message=outcome.msg, asynchronous=False)
                result_q.task_done()
        except queue.Empty:
            pass

        if work_q.full() and not paused:
            pause_consumer(consumer)
            paused = True
        if not work_q.full() and paused:
            resume_consumer(consumer)
            paused = False

        msg = consumer.poll(0.5)
        if msg is None:
            continue
        if msg.error():
            if msg.error().code() != KafkaError._PARTITION_EOF:
                logger.warning("Kafka error: %s", msg.error())
            continue

        work_q.put(WorkItem(msg=msg))

    consumer.close()


def init_tracing(settings: Settings) -> None:
    if not settings.otel_exporter_otlp_endpoint:
        return
    try:
        from opentelemetry import trace
        from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor

        resource = Resource.create({"service.name": "stt-worker"})
        provider = TracerProvider(resource=resource)
        provider.add_span_processor(
            BatchSpanProcessor(OTLPSpanExporter(endpoint=settings.otel_exporter_otlp_endpoint))
        )
        trace.set_tracer_provider(provider)
    except Exception:
        logger.warning("OpenTelemetry not available")


logger = with_context(
    __import__("logging").getLogger("stt-worker"),
    stage="init",
)


def main() -> None:
    configure_logging(settings.log_level)
    start_metrics(settings.metrics_port)
    start_health_server(settings.health_port)
    init_tracing(settings)
    logger.info("Starting worker")
    consume_loop(settings)


if __name__ == "__main__":
    main()
