from __future__ import annotations

import json
import logging
import sys
from datetime import datetime
from typing import Any, Dict


class JsonFormatter(logging.Formatter):
    _EXTRA_FIELDS = (
        "jobId",
        "job_id",
        "correlationId",
        "stage",
        "durationMs",
        "attempt",
        "engine",
        "lang",
        "audio_duration",
        "processing_time",
        "fallback_used",
        "error_code",
        "error_message",
        "model_ready",
    )

    def format(self, record: logging.LogRecord) -> str:
        payload: Dict[str, Any] = {
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for field in self._EXTRA_FIELDS:
            if hasattr(record, field):
                payload[field] = getattr(record, field)
        if record.exc_info:
            payload["error"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=True)


def configure_logging(level: str = "INFO") -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level.upper())


class ContextAdapter(logging.LoggerAdapter):
    def process(self, msg: str, kwargs: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
        merged = dict(self.extra)
        call_extra = kwargs.get("extra")
        if isinstance(call_extra, dict):
            merged.update(call_extra)
        kwargs["extra"] = merged
        return msg, kwargs


def with_context(logger: logging.Logger, **kwargs: Any) -> logging.LoggerAdapter:
    return ContextAdapter(logger, kwargs)
