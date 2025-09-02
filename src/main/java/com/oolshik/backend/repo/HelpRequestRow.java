package com.oolshik.backend.repo;

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
}