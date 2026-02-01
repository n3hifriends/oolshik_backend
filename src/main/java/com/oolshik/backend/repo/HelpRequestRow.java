package com.oolshik.backend.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface HelpRequestRow {
    UUID getId();
    String getTitle();
    String getDescription();
    Integer getRadiusMeters();
    String getStatus();
    UUID getRequesterId();
    String getCreatedByName();
    String getCreatedByPhoneNumber();
    UUID getHelperId();
    UUID getPendingHelperId();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    Instant getHelperAcceptedAt();
    Instant getAssignmentExpiresAt();
    Instant getPendingAuthExpiresAt();
    Instant getCancelledAt();
    Integer getReassignedCount();
    Integer getReleasedCount();
    Integer getRadiusStage();
    Instant getNextEscalationAt();
    String getVoiceUrl();
    BigDecimal getRatingValue();     // this task’s rating (nullable)
    BigDecimal getHelperAvgRating(); // computed avg for the helper (nullable)
    BigDecimal getRequesterAvgRating(); // computed avg for the requester (nullable)
    BigDecimal getRatingByRequester(); // rating given by requester (nullable)
    BigDecimal getRatingByHelper();    // rating given by helper (nullable)
    Double getDistanceMtr();                    // computed in SELECT for ordering
    Double getLatitude();
    Double getLongitude();
}
