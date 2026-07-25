package com.oolshik.backend.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminNotificationDtos {
    private AdminNotificationDtos() {}

    public record SendBroadcastRequest(
            @NotBlank String targetType,
            String targetValue,
            @NotEmpty List<String> channels,
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 1000) String body,
            UUID templateId,
            boolean saveAsTemplate,
            @Size(max = 100) String templateName,
            /** Mobile screen to open on notification tap. Defaults to InAppInbox when null. */
            @Size(max = 30) String routeKey,
            /** Entity ID required by the route (e.g. taskId for TaskDetail). Null for inbox routes. */
            @Size(max = 100) String routeTargetId
    ) {}

    public record SendBroadcastResponse(
            UUID broadcastId,
            String status,
            int estimatedRecipients
    ) {}

    public record BroadcastSummary(
            UUID id,
            String targetType,
            String targetValue,
            String channels,
            String status,
            int totalRecipients,
            int pushSent,
            int pushFailed,
            int smsSent,
            int smsFailed,
            int inAppCreated,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {}

    public record BroadcastDetail(
            UUID id,
            String title,
            String body,
            String targetType,
            String targetValue,
            String channels,
            String status,
            int totalRecipients,
            int pushSent,
            int pushFailed,
            int smsSent,
            int smsFailed,
            int inAppCreated,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime processingStartedAt,
            OffsetDateTime completedAt
    ) {}

    public record DeliveryRow(
            UUID id,
            UUID userId,
            String channel,
            String status,
            String error,
            OffsetDateTime sentAt
    ) {}

    public record TemplateResponse(
            UUID id,
            String name,
            String title,
            String body,
            UUID createdBy,
            OffsetDateTime createdAt
    ) {}

    public record CreateTemplateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 1000) String body
    ) {}

    public record UserNotificationResponse(
            UUID id,
            String title,
            String body,
            boolean read,
            OffsetDateTime createdAt,
            UUID broadcastId,
            String eventType,
            UUID taskId,
            UUID paymentRequestId,
            String route
    ) {}

    public record UnreadCountResponse(long count) {}
}
