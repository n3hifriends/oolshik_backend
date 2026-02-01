package com.oolshik.backend.entity;

import com.oolshik.backend.domain.HelpRequestActorRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "help_request_rating")
public class HelpRequestRatingEntity {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "rater_user_id", nullable = false)
    private UUID raterUserId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "rating_value", nullable = false, precision = 2, scale = 1)
    private BigDecimal ratingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "rater_role", nullable = false)
    private HelpRequestActorRole raterRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
    public UUID getRaterUserId() { return raterUserId; }
    public void setRaterUserId(UUID raterUserId) { this.raterUserId = raterUserId; }
    public UUID getTargetUserId() { return targetUserId; }
    public void setTargetUserId(UUID targetUserId) { this.targetUserId = targetUserId; }
    public BigDecimal getRatingValue() { return ratingValue; }
    public void setRatingValue(BigDecimal ratingValue) { this.ratingValue = ratingValue; }
    public HelpRequestActorRole getRaterRole() { return raterRole; }
    public void setRaterRole(HelpRequestActorRole raterRole) { this.raterRole = raterRole; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
