package com.oolshik.backend.web.dto;

import com.oolshik.backend.domain.HelpRequestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class HelpRequestDtos {

    public record CreateRequest(
        @NotBlank String title,
        String description,
        String voiceUrl,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @Min(50) @Max(10000) Integer radiusMeters
    ) {}

    public record HelpRequestView(
            UUID id,
            String title,
            String description,
            int radiusMeters,
            HelpRequestStatus status,
            UUID requesterId,
            UUID helperId,
            OffsetDateTime createdAt,
            String voiceUrl,
            BigDecimal ratingValue
    ) {}
}
