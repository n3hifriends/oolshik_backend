from __future__ import annotations

import logging
import os
import queue
import signal
import subprocess
import threading
import time
import wave
import warnings
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, Optional, Tuple

import torch
import torchaudio
from cachetools import TTLCache
from confluent_kafka import KafkaError, Message
from pydantic import ValidationError

from stt_worker.audio.http_fetcher import AudioDownloadError, HttpAudioFetcher
from stt_worker.config import Settings, settings
from stt_worker.dlq import build_dlq_message
from stt_worker.health import set_engine, set_ready, start_health_server
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
from stt_worker.transcribe.engine import (
    ENGINE_FASTERWHISPER,
    ENGINE_INDICCONFORMER,
    BaseEngine,
    FasterWhisperEngine,
    IndicConformerEngine,
    TranscribeError,
)
from stt_worker.validator import parse_job, safe_payload


MODEL_READY = False


@dataclass
class Outcome:
    msg: Message
    commit: bool


@dataclass
class WorkItem:
    msg: Message


@dataclass
class RuntimeOptions:
    default_lang: str
    download_retries: int
    download_backoff_sec: float
    fallback_enabled: bool


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _normalize_engine_name(value: str) -> str:
    v = (value or "").strip().lower()
    if v in {"fasterwhisper", "faster-whisper"}:
        return ENGINE_FASTERWHISPER
    return ENGINE_INDICCONFORMER


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
    cmd = [
        "ffmpeg",
        "-y",
        "-i",
        input_path,
        "-ac",
        "1",
        "-ar",
        "16000",
        "-vn",
        "-sn",
        output_path,
    ]
    try:
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except subprocess.CalledProcessError as exc:
        raise AudioDownloadError("UNSUPPORTED_FORMAT", "ffmpeg conversion failed", False) from exc


def wav_duration_seconds(path: str) -> float:
    with wave.open(path, "rb") as wf:
        frames = wf.getnframes()
        rate = wf.getframerate()
        if rate == 0:
            return 0.0
        return frames / float(rate)


def preprocess_audio(wav_path: str) -> Tuple[torch.Tensor, float]:
    waveform, sample_rate = torchaudio.load(wav_path)
    if waveform.ndim == 1:
        waveform = waveform.unsqueeze(0)

    if waveform.shape[0] > 1:
        waveform = waveform.mean(dim=0, keepdim=True)

    if sample_rate != 16000:
        waveform = torchaudio.functional.resample(waveform, sample_rate, 16000)
        sample_rate = 16000

    peak = waveform.abs().max().item() if waveform.numel() > 0 else 0.0
    if peak > 0:
        waveform = waveform / peak

    duration = 0.0
    if waveform.numel() > 0:
        duration = float(waveform.shape[1]) / float(sample_rate)

    return waveform, duration


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
    engine_name: str,
    model_version: str,
) -> bool:
    if error.retryable and attempt < settings.max_attempts:
        delay_ms = backoff_ms(attempt + 1, settings.retry_backoff_base_ms, settings.retry_backoff_max_ms)
        logger.info(
            "Retrying job",
            extra={
                "stage": "retry",
                "job_id": (job_payload or {}).get("jobId"),
                "error_code": error.code,
            },
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
                engine=engine_name,
                model_version=model_version,
                error=error,
            )
            produce_json(producer, settings.stt_results_topic, job.jobId, result)
            flush_producer(producer)
        except ValidationError:
            pass

    STT_FAILURES_TOTAL.labels(error.code).inc()
    STT_JOBS_TOTAL.labels("failed").inc()
    logger.warning(
        "Job failed",
        extra={
            "stage": failure_stage.lower(),
            "job_id": (job_payload or {}).get("jobId"),
            "engine": engine_name,
            "error_code": error.code,
            "fallback_used": False,
        },
    )
    return True


def process_message(
    settings: Settings,
    producer,
    engine: BaseEngine,
    fallback_engine: Optional[BaseEngine],
    fetcher: HttpAudioFetcher,
    options: RuntimeOptions,
    completed_cache: TTLCache,
    msg: Message,
) -> bool:
    raw_payload, raw_error = safe_payload(msg.value())
    log = with_context(
        logger,
        job_id=(raw_payload or {}).get("jobId"),
        correlationId=(raw_payload or {}).get("correlationId"),
    )

    if raw_error:
        error = ErrorInfo(code="INVALID_PAYLOAD", message=raw_error, retryable=False)
        handle_failure(
            log,
            settings,
            producer,
            msg,
            None,
            get_attempt(msg),
            error,
            "VALIDATION",
            engine.engine_name,
            engine.model_version,
        )
        return True

    try:
        job = parse_job(msg.value())
    except ValidationError as exc:
        error = ErrorInfo(code="INVALID_PAYLOAD", message=str(exc), retryable=False)
        handle_failure(
            log,
            settings,
            producer,
            msg,
            raw_payload,
            get_attempt(msg),
            error,
            "VALIDATION",
            engine.engine_name,
            engine.model_version,
        )
        return True

    if job.jobId in completed_cache:
        log.info("Job already processed", extra={"stage": "dedupe"})
        return True

    start = time.perf_counter()
    attempt = get_attempt(msg)
    selected_lang = (job.languageHint or options.default_lang or "mr").strip().lower()

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
            retries=options.download_retries,
            backoff_base_sec=options.download_backoff_sec,
        )
        STT_DOWNLOAD_SECONDS.observe(time.perf_counter() - download_start)

        ffmpeg_convert(input_path, wav_path)
        wav_duration = wav_duration_seconds(wav_path)
        if wav_duration > settings.max_audio_duration_sec:
            raise AudioDownloadError("AUDIO_TOO_LONG", f"Audio longer than {settings.max_audio_duration_sec}s", False)

        wav_tensor, preprocessed_duration = preprocess_audio(wav_path)
        if preprocessed_duration > settings.max_audio_duration_sec:
            raise AudioDownloadError("AUDIO_TOO_LONG", f"Audio longer than {settings.max_audio_duration_sec}s", False)

        transcribe_start = time.perf_counter()
        fallback_used = False
        used_engine = engine

        try:
            result = engine.transcribe(wav_tensor, selected_lang)
        except TranscribeError as primary_exc:
            if engine.engine_name == ENGINE_INDICCONFORMER and options.fallback_enabled and fallback_engine:
                fallback_used = True
                log.warning(
                    "Primary engine failed; attempting fallback",
                    extra={
                        "stage": "transcribe",
                        "job_id": job.jobId,
                        "engine": engine.engine_name,
                        "lang": selected_lang,
                        "fallback_used": True,
                        "error_code": primary_exc.code,
                    },
                )
                used_engine = fallback_engine
                result = fallback_engine.transcribe(wav_tensor, selected_lang)
            else:
                raise

        STT_TRANSCRIBE_SECONDS.observe(time.perf_counter() - transcribe_start)

        transcript_text = result.get("text") or None
        detected_language = result.get("language")
        confidence_value = result.get("confidence")
        confidence = float(confidence_value) if isinstance(confidence_value, (int, float)) else None

        result_payload = build_result(
            job=job,
            status=ResultStatus.COMPLETED,
            transcript_text=transcript_text,
            detected_language=detected_language,
            confidence=confidence,
            engine=used_engine.engine_name,
            model_version=used_engine.model_version,
            error=None,
        )
        produce_json(producer, settings.stt_results_topic, job.jobId, result_payload)
        flush_producer(producer)

        completed_cache[job.jobId] = True
        processing_time = time.perf_counter() - start
        STT_JOBS_TOTAL.labels("completed").inc()
        STT_PROCESSING_SECONDS.observe(processing_time)
        log.info(
            "Job completed",
            extra={
                "stage": "completed",
                "job_id": job.jobId,
                "engine": used_engine.engine_name,
                "lang": selected_lang,
                "audio_duration": round(preprocessed_duration, 3),
                "processing_time": round(processing_time, 3),
                "fallback_used": fallback_used,
                "error_code": None,
            },
        )
        return True
    except AudioDownloadError as exc:
        error = classify_error(exc)
        log.warning(
            "Download error",
            extra={
                "stage": "download",
                "job_id": job.jobId,
                "engine": engine.engine_name,
                "lang": selected_lang,
                "fallback_used": False,
                "error_code": error.code,
                "error_message": error.message,
            },
        )
        return handle_failure(
            log,
            settings,
            producer,
            msg,
            raw_payload,
            attempt,
            error,
            "DOWNLOAD",
            engine.engine_name,
            engine.model_version,
        )
    except TranscribeError as exc:
        error = classify_error(exc)
        log.warning(
            "Transcribe error",
            extra={
                "stage": "transcribe",
                "job_id": job.jobId,
                "engine": engine.engine_name,
                "lang": selected_lang,
                "fallback_used": False,
                "error_code": error.code,
                "error_message": error.message,
            },
        )
        return handle_failure(
            log,
            settings,
            producer,
            msg,
            raw_payload,
            attempt,
            error,
            "TRANSCRIBE",
            engine.engine_name,
            engine.model_version,
        )
    except Exception as exc:  # noqa: BLE001
        error = classify_error(exc)
        return handle_failure(
            log,
            settings,
            producer,
            msg,
            raw_payload,
            attempt,
            error,
            "PUBLISH",
            engine.engine_name,
            engine.model_version,
        )
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
    engine: BaseEngine,
    fallback_engine: Optional[BaseEngine],
    fetcher: HttpAudioFetcher,
    options: RuntimeOptions,
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
        commit = process_message(settings, producer, engine, fallback_engine, fetcher, options, completed_cache, item.msg)
        result_q.put(Outcome(msg=item.msg, commit=commit))
        work_q.task_done()


def consume_loop(settings: Settings, engine: BaseEngine, fallback_engine: Optional[BaseEngine], options: RuntimeOptions) -> None:
    global MODEL_READY
    if not MODEL_READY:
        raise RuntimeError("Worker is not ready to consume")

    consumer = create_consumer(settings)
    producer = create_producer(settings)
    fetcher = HttpAudioFetcher()

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
            args=(settings, producer, engine, fallback_engine, fetcher, options, completed_cache, work_q, result_q, stop_event),
            daemon=True,
        )
        thread.start()
        workers.append(thread)

    consumer.subscribe([settings.stt_jobs_topic])

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
                logger.warning("Kafka error", extra={"stage": "consume"})
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


def initialize_engines(settings: Settings) -> Tuple[BaseEngine, Optional[BaseEngine], RuntimeOptions]:
    requested_engine = _normalize_engine_name(os.getenv("STT_ENGINE", ENGINE_INDICCONFORMER))
    default_lang = (os.getenv("STT_DEFAULT_LANG", "mr") or "mr").strip().lower()
    asr_revision = (os.getenv("ASR_MODEL_REVISION") or "").strip() or None

    options = RuntimeOptions(
        default_lang=default_lang,
        download_retries=max(0, int(os.getenv("AUDIO_DOWNLOAD_RETRIES", "2"))),
        download_backoff_sec=max(0.1, float(os.getenv("AUDIO_DOWNLOAD_BACKOFF_SEC", "0.5"))),
        fallback_enabled=_env_bool("STT_ENABLE_FALLBACK", True),
    )

    if requested_engine == ENGINE_FASTERWHISPER:
        primary = FasterWhisperEngine(
            settings.model_size,
            settings.device,
            settings.compute_type,
            timeout_sec=settings.transcribe_timeout_sec,
            default_lang=default_lang,
        )
        return primary, None, options

    try:
        primary = IndicConformerEngine(
            model_id=os.getenv("ASR_MODEL_ID", "ai4bharat/indic-conformer-600m-multilingual"),
            revision=asr_revision,
            decoding=os.getenv("ASR_DECODING", "rnnt"),
            timeout_sec=settings.transcribe_timeout_sec,
            default_lang=default_lang,
            allow_runtime_model_download=_env_bool("STT_ALLOW_RUNTIME_MODEL_DOWNLOAD", False),
        )
    except Exception as exc:  # noqa: BLE001
        if not options.fallback_enabled:
            raise
        logger.warning(
            "IndicConformer init failed; falling back to FasterWhisper",
            extra={
                "engine": ENGINE_FASTERWHISPER,
                "model_ready": False,
                "fallback_used": True,
                "error_code": getattr(exc, "code", "MODEL_LOAD_FAILED"),
            },
        )
        primary = FasterWhisperEngine(
            settings.model_size,
            settings.device,
            settings.compute_type,
            timeout_sec=settings.transcribe_timeout_sec,
            default_lang=default_lang,
        )
        return primary, None, options

    fallback: Optional[BaseEngine] = None
    if options.fallback_enabled:
        try:
            fallback = FasterWhisperEngine(
                settings.model_size,
                settings.device,
                settings.compute_type,
                timeout_sec=settings.transcribe_timeout_sec,
                default_lang=default_lang,
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning(
                "Fallback engine init failed; continuing without fallback",
                extra={
                    "engine": ENGINE_INDICCONFORMER,
                    "model_ready": True,
                    "fallback_used": False,
                    "error_code": getattr(exc, "code", "FALLBACK_INIT_FAILED"),
                },
            )

    return primary, fallback, options


logger = with_context(logging.getLogger("stt-worker"), stage="init")


def main() -> None:
    global MODEL_READY

    warnings.filterwarnings(
        "ignore",
        message="`resume_download` is deprecated.*",
        category=FutureWarning,
    )
    configure_logging(settings.log_level)
    start_metrics(settings.metrics_port)
    set_ready(False)
    set_engine("unknown")
    start_health_server(settings.health_port)
    init_tracing(settings)

    requested_engine = _normalize_engine_name(os.getenv("STT_ENGINE", ENGINE_INDICCONFORMER))
    set_engine(requested_engine)
    logger.info("Starting worker", extra={"engine": requested_engine, "model_ready": False})

    try:
        engine, fallback_engine, options = initialize_engines(settings)
    except Exception:
        MODEL_READY = False
        set_ready(False)
        logger.exception(
            "Model initialization failed",
            extra={"engine": requested_engine, "model_ready": False, "error_code": "MODEL_LOAD_FAILED"},
        )
        raise SystemExit(1)

    MODEL_READY = True
    set_engine(engine.engine_name)
    set_ready(True)
    logger.info(
        "Worker ready",
        extra={"engine": engine.engine_name, "model_ready": True, "fallback_used": bool(fallback_engine)},
    )

    consume_loop(settings, engine, fallback_engine, options)


if __name__ == "__main__":
    main()
