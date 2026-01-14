from __future__ import annotations

import json
import logging
import sys
from datetime import datetime
from typing import Any, Dict


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: Dict[str, Any] = {
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if hasattr(record, "jobId"):
            payload["jobId"] = getattr(record, "jobId")
        if hasattr(record, "correlationId"):
            payload["correlationId"] = getattr(record, "correlationId")
        if hasattr(record, "stage"):
            payload["stage"] = getattr(record, "stage")
        if hasattr(record, "durationMs"):
            payload["durationMs"] = getattr(record, "durationMs")
        if hasattr(record, "attempt"):
            payload["attempt"] = getattr(record, "attempt")
        if record.exc_info:
            payload["error"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=True)


def configure_logging(level: str = "INFO") -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level.upper())


def with_context(logger: logging.Logger, **kwargs: Any) -> logging.LoggerAdapter:
    return logging.LoggerAdapter(logger, kwargs)
