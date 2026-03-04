package com.oolshik.backend.web.dto;

import com.oolshik.backend.domain.HelpRequestStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ActiveRequestDtos {

    private ActiveRequestDtos() {
    }

    public record ActiveRequestSummaryItem(
            UUID id,
            HelpRequestStatus status,
            OffsetDateTime createdAt
    ) {
    }

    public record ActiveRequestSummaryResponse(
            int cap,
            int activeCount,
            boolean blocked,
            UUID suggestedRequestId,
            List<ActiveRequestSummaryItem> activeRequests
    ) {
    }

    public record ActiveRequestCapReachedResponse(
            String code,
            String message,
            int cap,
            int activeCount,
            List<UUID> activeRequestIds,
            UUID suggestedRequestId
    ) {
    }
}
