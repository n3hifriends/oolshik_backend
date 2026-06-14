package com.oolshik.backend.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }

    public record StatsResponse(
            long totalUsers,
            long netas,
            long karyakartas,
            long admins,
            long openRequests,
            long activeRequests,
            long reviewRequired,
            long completed,
            long sttFailures,
            long notificationFailures,
            long openReports,
            double paymentsCapturedInr,
            List<TrendPoint> trend
    ) {
    }

    public record TrendPoint(String day, long created, long completed) {
    }

    public record AdminUserSummary(
            UUID id,
            String displayName,
            String phoneNumber,
            String email,
            List<String> roles,
            OffsetDateTime joinedAt
    ) {
    }

    public record AdminUserDetail(
            UUID id,
            String displayName,
            String phoneNumber,
            String email,
            boolean emailVerified,
            List<String> roles,
            String languages,
            String preferredLanguage,
            OffsetDateTime joinedAt,
            OffsetDateTime updatedAt,
            long requestsMade,
            long jobsDone
    ) {
    }

    public record UpdateRolesRequest(List<String> roles) {
    }

    public record UserRef(UUID id, String displayName, String phoneNumber) {
    }

    public record GeoPoint(double lat, double lng) {
    }

    public record AdminRequestSummary(
            UUID id,
            String title,
            String status,
            UserRef requester,
            UserRef helper,
            GeoPoint geo,
            OffsetDateTime createdAt
    ) {
    }

    public record AdminRequestDetail(
            UUID id,
            String title,
            String description,
            String status,
            UserRef requester,
            UserRef helper,
            GeoPoint geo,
            int radiusM,
            BigDecimal offerAmount,
            String offerCurrency,
            OffsetDateTime createdAt,
            String audioUrl,
            String transcript
    ) {
    }

    public record AdminOtpAuditRow(
            UUID id,
            String maskedPhone,
            String provider,
            String action,
            String status,
            String detail,
            OffsetDateTime createdAt
    ) {
    }

    public record AdminTranscriptionRow(
            UUID id,
            UUID requestId,
            String status,
            String engine,
            String languageHint,
            String detectedLanguage,
            String transcript,
            BigDecimal confidence,
            int attemptCount,
            String error,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String audioUrl
    ) {
    }

    public record RetryTranscriptionResponse(
            int retried,
            int totalFailed
    ) {
    }

    public record AdminPaymentRow(
            UUID id,
            UUID requestId,
            String payerRole,
            BigDecimal amountInr,
            String mode,
            String status,
            String ref,
            Instant createdAt
    ) {
    }

    public record AdminReportRow(
            UUID id,
            UserRef reporter,
            String targetType,
            UUID targetId,
            String reason,
            String details,
            OffsetDateTime reportedAt
    ) {
    }

    public record AdminNotificationRow(
            UUID id,
            String event,
            UUID aggregateId,
            String status,
            int attempts,
            String lastError,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
