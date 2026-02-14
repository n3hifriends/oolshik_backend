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

    @Column(name = "pending_helper_id")
    private UUID pendingHelperId;

    @Column(name = "pending_auth_expires_at")
    private OffsetDateTime pendingAuthExpiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "authorized_at")
    private OffsetDateTime authorizedAt;

    @Column(name = "authorized_by")
    private UUID authorizedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "reject_reason_code")
    private String rejectReasonCode;

    @Column(name = "reject_reason_text", columnDefinition = "TEXT")
    private String rejectReasonText;

    @Column(name = "auth_timeout_count")
    private Integer authTimeoutCount;

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

    @Column(name = "assignment_expires_at")
    private OffsetDateTime assignmentExpiresAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason_code")
    private String cancelReasonCode;

    @Column(name = "cancel_reason_text", columnDefinition = "TEXT")
    private String cancelReasonText;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "reassigned_count")
    private Integer reassignedCount;

    @Column(name = "released_count")
    private Integer releasedCount;

    @Column(name = "last_state_change_at")
    private OffsetDateTime lastStateChangeAt;

    @Column(name = "last_state_change_reason")
    private String lastStateChangeReason;

    @Column(name = "radius_stage")
    private Integer radiusStage;

    @Column(name = "next_escalation_at")
    private OffsetDateTime nextEscalationAt;

    @Column(name = "offer_amount", precision = 12, scale = 2)
    private BigDecimal offerAmount;

    @Column(name = "offer_currency", length = 8)
    private String offerCurrency;

    @Column(name = "offer_updated_at")
    private OffsetDateTime offerUpdatedAt;

    @Column(name = "offer_last_notified_amount", precision = 12, scale = 2)
    private BigDecimal offerLastNotifiedAmount;

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
        if (lastStateChangeAt == null) lastStateChangeAt = createdAt;
        if (reassignedCount == null) reassignedCount = 0;
        if (releasedCount == null) releasedCount = 0;
        if (radiusStage == null) radiusStage = 0;
        if (authTimeoutCount == null) authTimeoutCount = 0;
        if (offerCurrency == null || offerCurrency.isBlank()) offerCurrency = "INR";
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
    public UUID getPendingHelperId() { return pendingHelperId; }
    public OffsetDateTime getPendingAuthExpiresAt() { return pendingAuthExpiresAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public OffsetDateTime getAuthorizedAt() { return authorizedAt; }
    public UUID getAuthorizedBy() { return authorizedBy; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
    public UUID getRejectedBy() { return rejectedBy; }
    public String getRejectReasonCode() { return rejectReasonCode; }
    public String getRejectReasonText() { return rejectReasonText; }
    public Integer getAuthTimeoutCount() { return authTimeoutCount; }
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
    public void setPendingHelperId(UUID pendingHelperId) { this.pendingHelperId = pendingHelperId; }
    public void setPendingAuthExpiresAt(OffsetDateTime pendingAuthExpiresAt) {
        this.pendingAuthExpiresAt = pendingAuthExpiresAt;
    }
    public void setAcceptedAt(OffsetDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public void setAuthorizedAt(OffsetDateTime authorizedAt) { this.authorizedAt = authorizedAt; }
    public void setAuthorizedBy(UUID authorizedBy) { this.authorizedBy = authorizedBy; }
    public void setRejectedAt(OffsetDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public void setRejectedBy(UUID rejectedBy) { this.rejectedBy = rejectedBy; }
    public void setRejectReasonCode(String rejectReasonCode) { this.rejectReasonCode = rejectReasonCode; }
    public void setRejectReasonText(String rejectReasonText) { this.rejectReasonText = rejectReasonText; }
    public void setAuthTimeoutCount(Integer authTimeoutCount) { this.authTimeoutCount = authTimeoutCount; }
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

    public OffsetDateTime getAssignmentExpiresAt() { return assignmentExpiresAt; }
    public void setAssignmentExpiresAt(OffsetDateTime assignmentExpiresAt) {
        this.assignmentExpiresAt = assignmentExpiresAt;
    }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public UUID getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(UUID cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelReasonCode() { return cancelReasonCode; }
    public void setCancelReasonCode(String cancelReasonCode) { this.cancelReasonCode = cancelReasonCode; }
    public String getCancelReasonText() { return cancelReasonText; }
    public void setCancelReasonText(String cancelReasonText) { this.cancelReasonText = cancelReasonText; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(OffsetDateTime releasedAt) { this.releasedAt = releasedAt; }
    public Integer getReassignedCount() { return reassignedCount; }
    public void setReassignedCount(Integer reassignedCount) { this.reassignedCount = reassignedCount; }
    public Integer getReleasedCount() { return releasedCount; }
    public void setReleasedCount(Integer releasedCount) { this.releasedCount = releasedCount; }
    public OffsetDateTime getLastStateChangeAt() { return lastStateChangeAt; }
    public void setLastStateChangeAt(OffsetDateTime lastStateChangeAt) { this.lastStateChangeAt = lastStateChangeAt; }
    public String getLastStateChangeReason() { return lastStateChangeReason; }
    public void setLastStateChangeReason(String lastStateChangeReason) { this.lastStateChangeReason = lastStateChangeReason; }
    public Integer getRadiusStage() { return radiusStage; }
    public void setRadiusStage(Integer radiusStage) { this.radiusStage = radiusStage; }
    public OffsetDateTime getNextEscalationAt() { return nextEscalationAt; }
    public void setNextEscalationAt(OffsetDateTime nextEscalationAt) { this.nextEscalationAt = nextEscalationAt; }
    public BigDecimal getOfferAmount() { return offerAmount; }
    public void setOfferAmount(BigDecimal offerAmount) { this.offerAmount = offerAmount; }
    public String getOfferCurrency() { return offerCurrency; }
    public void setOfferCurrency(String offerCurrency) { this.offerCurrency = offerCurrency; }
    public OffsetDateTime getOfferUpdatedAt() { return offerUpdatedAt; }
    public void setOfferUpdatedAt(OffsetDateTime offerUpdatedAt) { this.offerUpdatedAt = offerUpdatedAt; }
    public BigDecimal getOfferLastNotifiedAmount() { return offerLastNotifiedAmount; }
    public void setOfferLastNotifiedAmount(BigDecimal offerLastNotifiedAmount) { this.offerLastNotifiedAmount = offerLastNotifiedAmount; }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }
}
