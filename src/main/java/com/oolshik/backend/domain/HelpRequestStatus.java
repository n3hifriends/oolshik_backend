package com.oolshik.backend.domain;

public enum HelpRequestStatus {
    DRAFT,
    OPEN,
    PENDING_AUTH,
    ASSIGNED,
    WORK_DONE_PENDING_CONFIRMATION,
    REVIEW_REQUIRED,
    COMPLETED,
    CANCELLED
}
