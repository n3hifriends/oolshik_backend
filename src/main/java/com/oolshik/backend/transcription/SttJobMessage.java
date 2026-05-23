package com.oolshik.backend.transcription;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SttJobMessage(
        UUID jobId,
        UUID taskId,
        UUID audioFileId,
        String storageProvider,
        String bucket,
        String objectKey,
        String region,
        String endpoint,
        Boolean pathStyleAccessEnabled,
        String audioUrl,
        String languageHint,
        OffsetDateTime createdAt,
        String correlationId
) {}
