from __future__ import annotations

import json
from typing import Any, Iterable, Tuple

from confluent_kafka import Producer

from stt_worker.config import Settings


def create_producer(settings: Settings) -> Producer:
    conf = {
        "bootstrap.servers": settings.kafka_bootstrap_servers,
    }
    return Producer(conf)


def produce_json(
    producer: Producer,
    topic: str,
    key: str,
    value: Any,
    headers: Iterable[Tuple[str, str | bytes]] | None = None,
) -> None:
    payload = json.dumps(value, ensure_ascii=True).encode("utf-8")
    producer.produce(topic=topic, key=key, value=payload, headers=headers)
    producer.poll(0)


def flush_producer(producer: Producer, timeout: float = 10.0) -> None:
    producer.flush(timeout)
