package com.oolshik.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_broadcast")
public class AdminBroadcastEntity {

    @Id
    private UUID id;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_value", length = 100)
    private String targetValue;

    @Column(nullable = false, length = 50)
    private String channels;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_recipients", nullable = false)
    private int totalRecipients;

    @Column(name = "push_sent", nullable = false)
    private int pushSent;

    @Column(name = "push_failed", nullable = false)
    private int pushFailed;

    @Column(name = "sms_sent", nullable = false)
    private int smsSent;

    @Column(name = "sms_failed", nullable = false)
    private int smsFailed;

    @Column(name = "in_app_created", nullable = false)
    private int inAppCreated;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }
    public String getChannels() { return channels; }
    public void setChannels(String channels) { this.channels = channels; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalRecipients() { return totalRecipients; }
    public void setTotalRecipients(int totalRecipients) { this.totalRecipients = totalRecipients; }
    public int getPushSent() { return pushSent; }
    public void setPushSent(int pushSent) { this.pushSent = pushSent; }
    public int getPushFailed() { return pushFailed; }
    public void setPushFailed(int pushFailed) { this.pushFailed = pushFailed; }
    public int getSmsSent() { return smsSent; }
    public void setSmsSent(int smsSent) { this.smsSent = smsSent; }
    public int getSmsFailed() { return smsFailed; }
    public void setSmsFailed(int smsFailed) { this.smsFailed = smsFailed; }
    public int getInAppCreated() { return inAppCreated; }
    public void setInAppCreated(int inAppCreated) { this.inAppCreated = inAppCreated; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(OffsetDateTime processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
