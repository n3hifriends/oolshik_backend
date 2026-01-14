from __future__ import annotations

from confluent_kafka import Consumer

from stt_worker.config import Settings


def create_consumer(settings: Settings) -> Consumer:
    conf = {
        "bootstrap.servers": settings.kafka_bootstrap_servers,
        "group.id": settings.kafka_group_id,
        "enable.auto.commit": False,
        "auto.offset.reset": "earliest",
        "enable.auto.offset.store": False,
    }
    return Consumer(conf)


def pause_consumer(consumer: Consumer) -> None:
    parts = consumer.assignment()
    if parts:
        consumer.pause(parts)


def resume_consumer(consumer: Consumer) -> None:
    parts = consumer.assignment()
    if parts:
        consumer.resume(parts)
