package com.oolshik.backend.entity;

import com.oolshik.backend.domain.ReportReason;
import com.oolshik.backend.domain.ReportPriority;
import com.oolshik.backend.domain.ReportStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_event")
public class ReportEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "help_request_id")
    private UUID helpRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private ReportReason reason;

    @Column(name = "details")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status = ReportStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private ReportPriority priority = ReportPriority.MEDIUM;

    @Column(name = "assigned_admin_user_id")
    private UUID assignedAdminUserId;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (status == null) status = ReportStatus.OPEN;
        if (priority == null) priority = ReportPriority.MEDIUM;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // getters/setters ...
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(UUID reporterUserId) { this.reporterUserId = reporterUserId; }

    public UUID getTargetUserId() { return targetUserId; }
    public void setTargetUserId(UUID targetUserId) { this.targetUserId = targetUserId; }

    public UUID getHelpRequestId() { return helpRequestId; }
    public void setHelpRequestId(UUID helpRequestId) { this.helpRequestId = helpRequestId; }

    public ReportReason getReason() { return reason; }
    public void setReason(ReportReason reason) { this.reason = reason; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public ReportPriority getPriority() { return priority; }
    public void setPriority(ReportPriority priority) { this.priority = priority; }
    public UUID getAssignedAdminUserId() { return assignedAdminUserId; }
    public void setAssignedAdminUserId(UUID assignedAdminUserId) { this.assignedAdminUserId = assignedAdminUserId; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
