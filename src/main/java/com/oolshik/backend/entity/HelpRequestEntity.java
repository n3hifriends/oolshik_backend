package com.oolshik.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.oolshik.backend.domain.HelpRequestStatus;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "help_request")
public class HelpRequestEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(nullable = false, name = "radius_meters")
    private int radiusMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HelpRequestStatus status;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Transient
    String displayName;

    @Transient
    String phoneNumber;

    @Column(name = "helper_id")
    private UUID helperId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Column(nullable = false)
    private String voiceUrl;

    @Column(name = "rating_value")
    private BigDecimal ratingValue; // scale 1

    @Column(name = "rated_by_user_id")
    private UUID ratedByUserId;

    @Column(name = "rated_at")
    private OffsetDateTime ratedAt;

    public Point getHelperAcceptLocation() {
        return helperAcceptLocation;
    }

    public void setHelperAcceptLocation(Point helperAcceptLocation) {
        this.helperAcceptLocation = helperAcceptLocation;
    }

    public OffsetDateTime getHelperAcceptedAt() {
        return helperAcceptedAt;
    }

    public void setHelperAcceptedAt(OffsetDateTime helperAcceptedAt) {
        this.helperAcceptedAt = helperAcceptedAt;
    }

    // NEW: Karyakarta’s location at accept time
    @Column(name = "helper_accept_location", columnDefinition = "geography(Point,4326)")
    private Point helperAcceptLocation;

    // NEW: When it was accepted
    @Column(name = "helper_accepted_at")
    private OffsetDateTime helperAcceptedAt;


    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (status == null) status = HelpRequestStatus.OPEN;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = OffsetDateTime.now(); }

    // getters/setters
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getRadiusMeters() { return radiusMeters; }
    public HelpRequestStatus getStatus() { return status; }
    public UUID getRequesterId() { return requesterId; }
    public String getDisplayName() { return displayName; }
    public String getPhoneNumber() { return phoneNumber; }
    public UUID getHelperId() { return helperId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setRadiusMeters(int radiusMeters) { this.radiusMeters = radiusMeters; }
    public void setStatus(HelpRequestStatus status) { this.status = status; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setHelperId(UUID helperId) { this.helperId = helperId; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getVoiceUrl() { return voiceUrl; }
    public void setVoiceUrl(String voiceUrl) { this.voiceUrl = voiceUrl; }
    public BigDecimal getRatingValue() { return ratingValue; }
    public void setRatingValue(BigDecimal ratingValue) { this.ratingValue = ratingValue; }
    public UUID getRatedByUserId() { return ratedByUserId; }
    public OffsetDateTime getRatedAt() { return ratedAt; }
    public void setRatedByUserId(UUID ratedByUserId) {
        this.ratedByUserId = ratedByUserId;
    }
    public void setRatedAt(OffsetDateTime ratedAt) {
        this.ratedAt = ratedAt;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }
}
