package com.oolshik.notificationworker.model;

public enum NotificationEventType {
    TASK_CREATED,
    TASK_AUTH_REQUESTED,
    TASK_AUTH_APPROVED,
    TASK_AUTH_REJECTED,
    TASK_AUTH_TIMEOUT,
    TASK_CANCELLED,
    TASK_RELEASED,
    TASK_REASSIGNED,
    TASK_TIMEOUT,
    TASK_RADIUS_EXPANDED
}
