import argparse
import json

from confluent_kafka import Consumer, KafkaError


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bootstrap", default="localhost:9092")
    parser.add_argument("--topic", default="stt.results")
    parser.add_argument("--group-id", default="stt-results-debugger")
    parser.add_argument("--timeout", type=float, default=1.0)
    parser.add_argument("--max-messages", type=int, default=10)
    args = parser.parse_args()

    conf = {
        "bootstrap.servers": args.bootstrap,
        "group.id": args.group_id,
        "auto.offset.reset": "earliest",
        "enable.auto.commit": True,
    }
    consumer = Consumer(conf)
    consumer.subscribe([args.topic])

    count = 0
    try:
        while count < args.max_messages:
            msg = consumer.poll(args.timeout)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                raise RuntimeError(msg.error())

            key = msg.key().decode("utf-8") if msg.key() else None
            try:
                payload = json.loads(msg.value().decode("utf-8"))
            except Exception:
                payload = msg.value().decode("utf-8", errors="replace")
            print({"key": key, "value": payload})
            count += 1
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
