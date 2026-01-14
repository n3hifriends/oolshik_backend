# stt-worker

Kafka STT worker that consumes jobs, downloads audio, transcribes with faster-whisper, and publishes results.

## How to run locally (Docker)
1) `docker compose up --build -d`
2) Publish a job:
   `python scripts/produce_test_job.py --audio-url https://example.com/sample.m4a`
3) Read results:
   `python scripts/consume_results.py --max-messages 1`

## How to run locally (Python)
1) `python -m venv .venv && source .venv/bin/activate`
2) `pip install -r requirements.txt`
3) `PYTHONPATH=src python -m stt_worker.main`

## Environment variables
- `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`)
- `KAFKA_GROUP_ID` (default `stt-worker`)
- `STT_JOBS_TOPIC` (default `stt.jobs`)
- `STT_RESULTS_TOPIC` (default `stt.results`)
- `STT_DLQ_TOPIC` (default `stt.jobs.dlq`)
- `MODEL_SIZE` (default `small`)
- `DEVICE` (default `cpu`, set `cuda` for GPU)
- `COMPUTE_TYPE` (optional)
- `AUDIO_DOWNLOAD_TIMEOUT` (seconds, default 30)
- `TRANSCRIBE_TIMEOUT` (seconds, default 120)
- `MAX_AUDIO_BYTES` (default 26214400)
- `MAX_AUDIO_DURATION_SEC` (default 600)
- `MAX_ATTEMPTS` (default 3)
- `RETRY_BACKOFF_BASE_MS` (default 500)
- `RETRY_BACKOFF_MAX_MS` (default 5000)
- `WORKER_CONCURRENCY` (default 1)
- `WORKER_QUEUE_MAX` (default 4)
- `METRICS_PORT` (default 9108)
- `HEALTH_PORT` (default 8081)
- `OTEL_EXPORTER_OTLP_ENDPOINT` (optional)
- `TMP_DIR` (default `/tmp/stt`)

## Message schemas
Job message (topic `stt.jobs`, key `jobId`):
```json
{
  "jobId": "uuid",
  "taskId": "uuid",
  "audioUrl": "https://...",
  "languageHint": "mr-IN|hi-IN|en-IN|null",
  "createdAt": "ISO-8601",
  "correlationId": "uuid-or-trace-id"
}
```

Result message (topic `stt.results`, key `jobId`):
```json
{
  "jobId": "uuid",
  "taskId": "uuid",
  "status": "COMPLETED|FAILED",
  "transcriptText": "string|null",
  "detectedLanguage": "string|null",
  "confidence": 0.0,
  "engine": "faster-whisper",
  "modelVersion": "string",
  "errorCode": "string|null",
  "errorMessage": "string|null",
  "completedAt": "ISO-8601",
  "correlationId": "uuid-or-trace-id"
}
```

DLQ message (topic `stt.jobs.dlq`, key `jobId`):
```json
{
  "originalTopic": "stt.jobs",
  "originalPartition": 0,
  "originalOffset": 123,
  "failedAt": "ISO-8601",
  "failureStage": "VALIDATION|DOWNLOAD|TRANSCRIBE|PUBLISH",
  "job": { "...": "original job payload" },
  "errorCode": "string",
  "errorMessage": "string",
  "attempt": 0
}
```

## Scaling notes
- Partition `stt.jobs` by `jobId` to keep per-job ordering.
- Use a consumer group (`KAFKA_GROUP_ID`) for horizontal scaling.
- Idempotency: a local TTL cache avoids duplicate processing; results are deterministic.
- Retry attempts are tracked via Kafka message header `attempt`.

## Troubleshooting
- If Kafka is not ready, check `docker compose logs -f kafka`.
- If audio download fails, verify the URL is accessible from the worker network.
- If ffmpeg errors occur, ensure the audio format is supported.

## End-to-end test (Kafka only)
Use this to verify the worker without the backend. Replace the URL if needed.

Publish a job from inside the worker container:
```bash
docker compose exec -T worker python - <<'PY'
import json, uuid
from datetime import datetime, timezone
from confluent_kafka import Producer

payload = {
  "jobId": str(uuid.uuid4()),
  "taskId": str(uuid.uuid4()),
  "audioUrl": "https://github.com/samnaveenkumaroff/Indic-F5/raw/refs/heads/main/1.wav",
  "languageHint": None,
  "createdAt": datetime.now(timezone.utc).isoformat(),
  "correlationId": str(uuid.uuid4()),
}
p = Producer({"bootstrap.servers": "kafka:9092"})
p.produce("stt.jobs", key=payload["jobId"], value=json.dumps(payload).encode("utf-8"))
p.flush(5)
print("Published", payload["jobId"])
PY
```

Consume a result:
```bash
docker compose exec -T worker python - <<'PY'
from confluent_kafka import Consumer, KafkaError
import json

c = Consumer({
  "bootstrap.servers": "kafka:9092",
  "group.id": "stt-results-debug",
  "auto.offset.reset": "earliest",
})
c.subscribe(["stt.results"])

msg = c.poll(20.0)
if msg is None:
    print("No result yet")
elif msg.error():
    print("Error:", msg.error())
else:
    key = msg.key().decode() if msg.key() else None
    val = json.loads(msg.value().decode())
    print({"key": key, "value": val})
c.close()
PY
```

Expected output (shape only):
```
{'key': '<jobId>', 'value': {'jobId': '<jobId>', 'taskId': '<taskId>', 'status': 'COMPLETED', 'transcriptText': '<text>', 'detectedLanguage': 'ta', 'confidence': 0.97, 'engine': 'faster-whisper', 'modelVersion': 'small', 'errorCode': None, 'errorMessage': None, 'completedAt': '<timestamp>', 'correlationId': '<correlationId>'}}
```
