from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, Optional

from stt_worker.retry import ErrorInfo


def build_dlq_message(
    original_topic: str,
    original_partition: int,
    original_offset: int,
    failure_stage: str,
    error: ErrorInfo,
    attempt: int,
    job_payload: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    return {
        "originalTopic": original_topic,
        "originalPartition": original_partition,
        "originalOffset": original_offset,
        "failedAt": datetime.now(timezone.utc).isoformat(),
        "failureStage": failure_stage,
        "job": job_payload,
        "errorCode": error.code,
        "errorMessage": error.message,
        "attempt": attempt,
    }
