package com.oolshik.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.notification.NotificationEventContext;
import com.oolshik.backend.notification.NotificationEventPayload;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.notification.NotificationOutboxStatus;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class HelpRequestNotificationService {

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public HelpRequestNotificationService(NotificationOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueueTaskEvent(
            NotificationEventType eventType,
            HelpRequestEntity task,
            NotificationEventContext context
    ) {
        OffsetDateTime occurredAt = context.getOccurredAt() != null
                ? context.getOccurredAt()
                : OffsetDateTime.now();
        UUID eventId = UUID.randomUUID();

        NotificationEventPayload payload = new NotificationEventPayload();
        payload.setEventId(eventId);
        payload.setEventType(eventType.name());
        payload.setTaskId(task.getId());
        payload.setOccurredAt(occurredAt);
        payload.setActorUserId(context.getActorUserId());
        payload.setRequesterUserId(task.getRequesterId());
        payload.setPreviousStatus(context.getPreviousStatus());
        payload.setNewStatus(context.getNewStatus());
        payload.setAssignmentChange(context.getAssignmentChange() == null
                ? com.oolshik.backend.notification.AssignmentChange.NONE.name()
                : context.getAssignmentChange().name());
        payload.setPreviousHelperId(context.getPreviousHelperId());
        payload.setNewHelperId(context.getNewHelperId());
        payload.setGeo(toGeo(task.getLocation()));
        payload.setPreviousRadiusMeters(context.getPreviousRadiusMeters());
        payload.setNewRadiusMeters(context.getNewRadiusMeters());
        payload.setOfferAmount(task.getOfferAmount());
        payload.setOfferCurrency(task.getOfferCurrency());

        String json = toJson(payload);

        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setId(eventId);
        outbox.setEventType(eventType.name());
        outbox.setAggregateId(task.getId());
        outbox.setPayloadJson(json);
        outbox.setStatus(NotificationOutboxStatus.PENDING.name());
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(occurredAt);
        outboxRepository.save(outbox);
    }

    private NotificationEventPayload.Geo toGeo(Point location) {
        if (location == null) {
            return new NotificationEventPayload.Geo(null, null);
        }
        return new NotificationEventPayload.Geo(location.getY(), location.getX());
    }

    private String toJson(NotificationEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }
    }
}
