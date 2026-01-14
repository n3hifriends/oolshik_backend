from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, Optional

from pydantic import BaseModel, ConfigDict, HttpUrl


class JobMessage(BaseModel):
    model_config = ConfigDict(extra="allow")

    jobId: str
    taskId: str
    audioUrl: HttpUrl
    languageHint: Optional[str] = None
    createdAt: datetime
    correlationId: Optional[str] = None


class ResultStatus(str, Enum):
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class ResultMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    jobId: str
    taskId: str
    status: ResultStatus
    transcriptText: Optional[str] = None
    detectedLanguage: Optional[str] = None
    confidence: Optional[float] = None
    engine: str
    modelVersion: str
    errorCode: Optional[str] = None
    errorMessage: Optional[str] = None
    completedAt: datetime
    correlationId: Optional[str] = None


class DlqMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    originalTopic: str
    originalPartition: int
    originalOffset: int
    failedAt: datetime
    failureStage: str
    job: Optional[Dict[str, Any]] = None
    errorCode: str
    errorMessage: str
    attempt: int = 0
