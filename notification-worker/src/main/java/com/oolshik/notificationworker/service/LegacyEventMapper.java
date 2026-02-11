package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.model.NotificationEventPayload;
import com.oolshik.notificationworker.model.NotificationEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LegacyEventMapper {

    private static final Logger log = LoggerFactory.getLogger(LegacyEventMapper.class);

    public NotificationEventPayload mapIfLegacy(NotificationEventPayload payload) {
        if (payload == null || payload.getEventType() == null) {
            return null;
        }
        String eventType = payload.getEventType();
        if (isCanonical(eventType)) {
            return payload;
        }
        switch (eventType) {
            case "TASK_REOPENED" -> {
                String change = payload.getAssignmentChange();
                if ("UNASSIGNED".equals(change)) {
                    payload.setEventType(NotificationEventType.TASK_RELEASED.name());
                    return payload;
                }
                if ("ASSIGNED".equals(change) || "CHANGED".equals(change)) {
                    payload.setEventType(NotificationEventType.TASK_REASSIGNED.name());
                    return payload;
                }
                log.warn("dropping legacy event TASK_REOPENED eventId={}", payload.getEventId());
                return null;
            }
            case "TASK_TIMEOUT_REOPENED" -> {
                payload.setEventType(NotificationEventType.TASK_TIMEOUT.name());
                return payload;
            }
            case "TASK_CANCELLED_CONFIRM" -> {
                payload.setEventType(NotificationEventType.TASK_CANCELLED.name());
                return payload;
            }
            default -> {
                log.warn("dropping unknown event type={} eventId={}", eventType, payload.getEventId());
                return null;
            }
        }
    }

    private boolean isCanonical(String eventType) {
        for (NotificationEventType type : NotificationEventType.values()) {
            if (type.name().equals(eventType)) {
                return true;
            }
        }
        return false;
    }
}
