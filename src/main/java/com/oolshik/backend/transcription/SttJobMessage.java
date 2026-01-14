package com.oolshik.backend.transcription;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SttJobMessage(
        UUID jobId,
        UUID taskId,
        String audioUrl,
        String languageHint,
        OffsetDateTime createdAt,
        String correlationId
) {}
