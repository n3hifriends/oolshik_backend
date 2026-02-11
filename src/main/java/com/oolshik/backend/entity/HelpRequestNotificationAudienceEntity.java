package com.oolshik.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "help_request_notification_audience")
public class HelpRequestNotificationAudienceEntity {

    @Id
    private UUID id;

    @Column(name = "help_request_id", nullable = false)
    private UUID helpRequestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "notified_for", nullable = false)
    private String notifiedFor;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getHelpRequestId() {
        return helpRequestId;
    }

    public void setHelpRequestId(UUID helpRequestId) {
        this.helpRequestId = helpRequestId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getNotifiedFor() {
        return notifiedFor;
    }

    public void setNotifiedFor(String notifiedFor) {
        this.notifiedFor = notifiedFor;
    }

    public Integer getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Integer radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
