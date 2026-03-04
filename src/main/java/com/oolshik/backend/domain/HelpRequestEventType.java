package com.oolshik.backend.domain;

public enum HelpRequestEventType {
    ACCEPTED,
    AUTH_REQUESTED,
    AUTH_APPROVED,
    AUTH_REJECTED,
    AUTH_TIMEOUT,
    RELEASED,
    REASSIGNED,
    CANCELLED,
    TIMEOUT,
    COMPLETED,
    RADIUS_EXPANDED,
    CREATE_BLOCKED_CAP_REACHED
}
