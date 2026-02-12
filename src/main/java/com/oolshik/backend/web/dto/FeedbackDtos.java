package com.oolshik.backend.web.dto;

import com.oolshik.backend.domain.FeedbackContextType;
import com.oolshik.backend.domain.FeedbackType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class FeedbackDtos {
    public record CreateRequest(
            @NotNull FeedbackType feedbackType,
            @NotNull FeedbackContextType contextType,
            UUID contextId,
            @Min(1) @Max(5) Integer rating,
            @Size(max = 10) List<@Size(max = 32) String> tags,
            @Size(max = 1000) String message,
            @Size(max = 16) String locale,
            @Size(max = 32) String appVersion,
            @Size(max = 32) String os,
            @Size(max = 64) String deviceModel
    ) {}

    public record CreateResponse(UUID id, OffsetDateTime createdAt) {}
}
