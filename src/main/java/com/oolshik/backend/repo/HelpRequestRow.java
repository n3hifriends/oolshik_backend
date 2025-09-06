package com.oolshik.backend.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface HelpRequestRow {
    UUID getId();
    String getTitle();
    String getDescription();
    Double getLatitude();
    Double getLongitude();
    Integer getRadiusMeters();
    String getStatus();
    UUID getRequesterId();
    String getCreatedByName();
    String getCreatedByPhoneNumber();
    UUID getHelperId();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    String getVoiceUrl();
    BigDecimal getRatingValue();     // this task’s rating (nullable)
    BigDecimal getHelperAvgRating(); // computed avg for the helper (nullable)
}