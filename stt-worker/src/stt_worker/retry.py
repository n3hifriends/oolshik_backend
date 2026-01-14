from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Optional


@dataclass
class ErrorInfo:
    code: str
    message: str
    retryable: bool


def backoff_ms(attempt: int, base_ms: int, max_ms: int) -> int:
    exp = min(max_ms, base_ms * (2 ** max(0, attempt - 1)))
    jitter = random.randint(0, int(exp * 0.2))
    return exp + jitter


def classify_error(exc: Exception) -> ErrorInfo:
    code = getattr(exc, "code", "INTERNAL_ERROR")
    message = str(exc)
    retryable = bool(getattr(exc, "retryable", True))
    return ErrorInfo(code=code, message=message, retryable=retryable)
