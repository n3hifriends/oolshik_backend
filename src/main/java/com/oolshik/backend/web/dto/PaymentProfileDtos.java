package com.oolshik.backend.web.dto;

import com.oolshik.backend.domain.PaymentProfileSourceType;
import com.oolshik.backend.logging.Sensitive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PaymentProfileDtos {

    public record PaymentProfileUpsertRequest(
            @Sensitive
            @NotBlank String upiId,
            @Size(max = 120) String payeeLabel,
            @NotNull PaymentProfileSourceType sourceType
    ) {
    }

    public record PaymentProfileMeResponse(
            boolean hasProfile,
            UUID id,
            String maskedUpiId,
            String payeeLabel,
            PaymentProfileSourceType sourceType,
            boolean isVerified,
            boolean isActive,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PaymentProfileEditResponse(
            boolean hasProfile,
            UUID id,
            String upiId,
            String maskedUpiId,
            String payeeLabel,
            PaymentProfileSourceType sourceType,
            boolean isVerified,
            boolean isActive,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
