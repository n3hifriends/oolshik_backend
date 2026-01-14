from __future__ import annotations

import json
from typing import Any, Dict, Tuple

from pydantic import ValidationError

from stt_worker.schema import JobMessage


def decode_json(payload: bytes | str | None) -> Dict[str, Any]:
    if payload is None:
        raise ValueError("Empty payload")
    if isinstance(payload, bytes):
        raw = payload.decode("utf-8")
    else:
        raw = payload
    return json.loads(raw)


def parse_job(payload: bytes | str | None) -> JobMessage:
    data = decode_json(payload)
    return JobMessage.model_validate(data)


def safe_payload(payload: bytes | str | None) -> Tuple[Dict[str, Any] | None, str | None]:
    try:
        data = decode_json(payload)
        return data, None
    except Exception as exc:  # noqa: BLE001
        return None, str(exc)
