package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.config.NotificationWorkerProperties;
import com.oolshik.notificationworker.model.NotificationEventPayload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationCoalescer {

    private final Map<UUID, PendingEvent> pending = new ConcurrentHashMap<>();
    private final NotificationDispatcher dispatcher;
    private final Duration window;

    public NotificationCoalescer(NotificationDispatcher dispatcher, NotificationWorkerProperties properties) {
        this.dispatcher = dispatcher;
        this.window = Duration.ofSeconds(properties.getCoalesceWindowSeconds());
    }

    public void enqueue(NotificationEventPayload payload) {
        if (payload.getTaskId() == null) {
            dispatcher.dispatch(payload);
            return;
        }
        pending.compute(payload.getTaskId(), (taskId, existing) -> {
            if (existing == null) {
                return new PendingEvent(payload, OffsetDateTime.now());
            }
            if (priority(payload) > priority(existing.payload)) {
                existing.payload = payload;
            }
            return existing;
        });
    }

    @Scheduled(fixedDelayString = "1000")
    public void flushReady() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Map.Entry<UUID, PendingEvent> entry : pending.entrySet()) {
            PendingEvent event = entry.getValue();
            if (Duration.between(event.firstSeen, now).compareTo(window) >= 0) {
                if (pending.remove(entry.getKey(), event)) {
                    dispatcher.dispatch(event.payload);
                }
            }
        }
    }

    private int priority(NotificationEventPayload payload) {
        String type = payload.getEventType();
        return switch (type) {
            case "TASK_CANCELLED" -> 100;
            case "TASK_AUTH_TIMEOUT" -> 90;
            case "TASK_REASSIGNED" -> 80;
            case "TASK_TIMEOUT" -> 70;
            default -> 10;
        };
    }

    private static class PendingEvent {
        private NotificationEventPayload payload;
        private final OffsetDateTime firstSeen;

        private PendingEvent(NotificationEventPayload payload, OffsetDateTime firstSeen) {
            this.payload = payload;
            this.firstSeen = firstSeen;
        }
    }
}
