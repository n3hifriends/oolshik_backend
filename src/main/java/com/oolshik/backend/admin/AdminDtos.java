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
            OffsetDateTime joinedAt,
            boolean blocked
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
            long jobsDone,
            boolean blocked,
            OffsetDateTime blockedAt,
            String blockReason,
            UserRef blockedBy
    ) {
    }

    public record BlockUserRequest(String reason) {
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
            String transcript,
            String adminOverrideReason
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

    public record AdminPaymentDetail(
            UUID id,
            UUID taskId,
            String payerRole,
            String mode,
            BigDecimal amountInr,
            String currency,
            String status,
            String ref,
            String payeeVpa,
            String payeeName,
            String note,
            String format,
            UserRef payerUser,
            UserRef requesterUser,
            UserRef helperUser,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt
    ) {
    }

    public record AdminReportRow(
            UUID id,
            UserRef reporter,
            UserRef targetUser,
            String targetType,
            UUID targetId,
            String reason,
            String details,
            String status,
            String priority,
            UserRef assignedAdmin,
            String targetTitle,
            String targetStatus,
            OffsetDateTime reportedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AdminReportDetail(
            UUID id,
            UserRef reporter,
            UserRef targetUser,
            String targetType,
            UUID targetId,
            String targetTitle,
            String targetStatus,
            String reason,
            String details,
            String status,
            String priority,
            UserRef assignedAdmin,
            String resolutionNote,
            OffsetDateTime reportedAt,
            OffsetDateTime updatedAt,
            OffsetDateTime resolvedAt,
            List<AdminReportActionRow> actions
    ) {
    }

    public record AdminReportActionRow(
            UUID id,
            UserRef admin,
            String action,
            String fromStatus,
            String toStatus,
            String note,
            OffsetDateTime createdAt
    ) {
    }

    public record UpdateReportStatusRequest(
            String status,
            String note
    ) {
    }

    public record AssignReportRequest(
            UUID adminUserId
    ) {
    }

    public record AddReportActionRequest(
            String action,
            String note
    ) {
    }

    // ---- Feedback ----
    public record AdminFeedbackRow(
            UUID id,
            UserRef submitter,
            String feedbackType,
            String contextType,
            UUID contextId,
            Integer rating,
            List<String> tags,
            String message,
            String status,
            String priority,
            UserRef assignedAdmin,
            String appVersion,
            String os,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AdminFeedbackDetail(
            UUID id,
            UserRef submitter,
            String feedbackType,
            String contextType,
            UUID contextId,
            String contextTitle,
            Integer rating,
            List<String> tags,
            String message,
            String status,
            String priority,
            UserRef assignedAdmin,
            String locale,
            String appVersion,
            String os,
            String deviceModel,
            String resolutionNote,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt,
            OffsetDateTime resolvedAt,
            List<AdminFeedbackActionRow> actions
    ) {
    }

    public record AdminFeedbackActionRow(
            UUID id,
            UserRef admin,
            String action,
            String fromStatus,
            String toStatus,
            String note,
            OffsetDateTime createdAt
    ) {
    }

    public record UpdateFeedbackStatusRequest(
            String status,
            String note
    ) {
    }

    public record AssignFeedbackRequest(
            UUID adminUserId
    ) {
    }

    public record AddFeedbackActionRequest(
            String action,
            String note
    ) {
    }

    public record UpdateHelpRequestStatusRequest(String status, String note) {
    }

    public record AdminNotificationRow(
            UUID id,
            String event,
            UUID aggregateId,
            String status,
            int attempts,
            String lastError,
            boolean retryEligible,
            OffsetDateTime acknowledgedAt,
            UUID acknowledgedBy,
            String resolutionNote,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AcknowledgeNotificationRequest(String note) {
    }
}
