package com.oolshik.backend.transcription;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SttResultMessage(
        UUID jobId,
        UUID taskId,
        TranscriptionStatus status,
        String transcriptText,
        String detectedLanguage,
        BigDecimal confidence,
        String engine,
        String modelVersion,
        String errorCode,
        String errorMessage,
        OffsetDateTime completedAt,
        String correlationId
) {}
