package com.oolshik.backend.entity;

import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestEventType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "help_request_event")
public class HelpRequestEventEntity {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private HelpRequestEventType eventType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private HelpRequestActorRole actorRole;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reason_text", columnDefinition = "TEXT")
    private String reasonText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
    public HelpRequestEventType getEventType() { return eventType; }
    public void setEventType(HelpRequestEventType eventType) { this.eventType = eventType; }
    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public HelpRequestActorRole getActorRole() { return actorRole; }
    public void setActorRole(HelpRequestActorRole actorRole) { this.actorRole = actorRole; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReasonText() { return reasonText; }
    public void setReasonText(String reasonText) { this.reasonText = reasonText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
