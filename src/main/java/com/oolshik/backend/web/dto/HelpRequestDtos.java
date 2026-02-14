package com.oolshik.backend.web.dto;

import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.domain.HelpRequestCancelReason;
import com.oolshik.backend.domain.HelpRequestReleaseReason;
import com.oolshik.backend.domain.HelpRequestRejectReason;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class HelpRequestDtos {

    public record CreateRequest(
        String title,
        String description,
        String voiceUrl,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @Min(50) @Max(10000) Integer radiusMeters,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "1000000.0", inclusive = true) BigDecimal offerAmount,
        String offerCurrency
    ) {}

    public record HelpRequestView(
            UUID id,
            String title,
            String description,
            int radiusMeters,
            HelpRequestStatus status,
            UUID requesterId,
            UUID helperId,
            UUID pendingHelperId,
            OffsetDateTime createdAt,
            String voiceUrl,
            BigDecimal ratingValue,
            BigDecimal ratingByRequester,
            BigDecimal ratingByHelper,
            BigDecimal requesterAvgRating,
            BigDecimal helperAvgRating,
            UUID transcriptionJobId,
            OffsetDateTime helperAcceptedAt,
            OffsetDateTime assignmentExpiresAt,
            OffsetDateTime pendingAuthExpiresAt,
            OffsetDateTime cancelledAt,
            UUID cancelledBy,
            Integer reassignedCount,
            Integer releasedCount,
            Integer radiusStage,
            OffsetDateTime nextEscalationAt,
            BigDecimal offerAmount,
            String offerCurrency,
            OffsetDateTime offerUpdatedAt
    ) {}

    public record OfferUpdateRequest(
            @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "1000000.0", inclusive = true) BigDecimal offerAmount,
            String offerCurrency
    ) {}

    public record OfferUpdateResponse(
            UUID taskId,
            BigDecimal offerAmount,
            String offerCurrency,
            OffsetDateTime offerUpdatedAt,
            boolean notificationSuppressed
    ) {}

    public record CancelRequest(
            @NotNull HelpRequestCancelReason reasonCode,
            String reasonText
    ) {}

    public record ReleaseRequest(
            HelpRequestReleaseReason reasonCode,
            String reasonText
    ) {}

    public record RejectRequest(
            @NotNull HelpRequestRejectReason reasonCode,
            String reasonText
    ) {}
}
