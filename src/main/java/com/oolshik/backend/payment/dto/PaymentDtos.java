package com.oolshik.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentDtos {

    public record ScanLocation(Double lat, Double lon) {
    }

    public record CreatePaymentRequest(
            @NotNull UUID taskId,
            @NotBlank String rawPayload,
            @NotBlank String format,        // upi-uri | emv | unknown
            String payeeVpa,
            String payeeName,
            String mcc,
            String merchantId,
            BigDecimal amount,              // optional
            String currency,                // default INR
            String note,                    // optional
            ScanLocation scanLocation,
            String appVersion,
            String deviceId
    ) {
    }

    public record PaymentRequestSnapshot(
            UUID id,
            UUID taskId,
            String payeeVpa,
            String payeeName,
            String mcc,
            String merchantId,
            BigDecimal amountRequested,
            String currency,
            String note,
            Instant createdAt,
            Instant expiresAt,
            String status,
            String upiIntent // canonical, server-built
    ) {
    }

    public record InitiatePaymentRequest(
            Instant clientTs
    ) {
    }

    public record MarkPaidRequest(
            BigDecimal paidAmount,
            String proofUrl
    ) {
    }
}