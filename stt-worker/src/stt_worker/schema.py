from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, Optional

from pydantic import BaseModel, ConfigDict, HttpUrl, model_validator


class JobMessage(BaseModel):
    model_config = ConfigDict(extra="allow")

    jobId: str
    taskId: str
    audioFileId: Optional[str] = None
    storageProvider: Optional[str] = None
    bucket: Optional[str] = None
    objectKey: Optional[str] = None
    region: Optional[str] = None
    endpoint: Optional[str] = None
    pathStyleAccessEnabled: Optional[bool] = None
    audioUrl: Optional[HttpUrl] = None
    languageHint: Optional[str] = None
    createdAt: datetime
    correlationId: Optional[str] = None

    @model_validator(mode="after")
    def ensure_audio_source(self) -> "JobMessage":
        has_object_ref = bool(self.bucket and self.objectKey)
        has_audio_url = self.audioUrl is not None
        if not has_object_ref and not has_audio_url:
            raise ValueError("Either audioUrl or bucket/objectKey is required")
        return self


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
