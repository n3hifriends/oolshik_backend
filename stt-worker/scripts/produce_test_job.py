import argparse
import json
import uuid
from datetime import datetime, timezone

from confluent_kafka import Producer


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bootstrap", default="localhost:9092")
    parser.add_argument("--topic", default="stt.jobs")
    parser.add_argument("--audio-url", required=True)
    parser.add_argument("--job-id", default=None)
    parser.add_argument("--task-id", default=None)
    parser.add_argument("--language-hint", default=None)
    args = parser.parse_args()

    job_id = args.job_id or str(uuid.uuid4())
    task_id = args.task_id or str(uuid.uuid4())

    payload = {
        "jobId": job_id,
        "taskId": task_id,
        "audioUrl": args.audio_url,
        "languageHint": args.language_hint,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "correlationId": str(uuid.uuid4()),
    }

    producer = Producer({"bootstrap.servers": args.bootstrap})
    producer.produce(args.topic, key=job_id, value=json.dumps(payload).encode("utf-8"))
    producer.flush(5)
    print(f"Published jobId={job_id}")


if __name__ == "__main__":
    main()
