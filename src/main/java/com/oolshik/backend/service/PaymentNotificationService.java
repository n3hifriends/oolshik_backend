package com.oolshik.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.notification.AssignmentChange;
import com.oolshik.backend.notification.NotificationEventPayload;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.notification.NotificationOutboxStatus;
import com.oolshik.backend.payment.PaymentRequest;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PaymentNotificationService {

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentNotificationService(NotificationOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueuePaymentEvent(
            NotificationEventType eventType,
            PaymentRequest payment,
            UUID actorUserId,
            String previousStatus,
            String newStatus
    ) {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);

        NotificationEventPayload payload = new NotificationEventPayload();
        payload.setEventId(eventId);
        payload.setEventType(eventType.name());
        payload.setTaskId(payment.getTaskId());
        payload.setOccurredAt(occurredAt);
        payload.setActorUserId(actorUserId);
        payload.setRequesterUserId(payment.getRequesterUser());
        payload.setPreviousStatus(previousStatus);
        payload.setNewStatus(newStatus);
        payload.setAssignmentChange(AssignmentChange.NONE.name());
        payload.setPreviousHelperId(payment.getHelperUser());
        payload.setNewHelperId(payment.getHelperUser());
        payload.setPaymentRequestId(payment.getId());
        payload.setPayerUserId(payment.getPayerUser());
        payload.setHelperUserId(payment.getHelperUser());
        payload.setPayerRole(payment.getPayerRole() == null ? null : payment.getPayerRole().name());
        payload.setPaymentAmount(payment.getAmountRequested());
        payload.setPaymentCurrency(payment.getCurrency());
        payload.setPaymentDueAt(
                payment.getExpiresAt() == null ? null : payment.getExpiresAt().atOffset(ZoneOffset.UTC)
        );
        payload.setGeo(new NotificationEventPayload.Geo(null, null));

        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setId(eventId);
        outbox.setEventType(eventType.name());
        outbox.setAggregateId(payment.getId());
        outbox.setPayloadJson(toJson(payload));
        outbox.setStatus(NotificationOutboxStatus.PENDING.name());
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(occurredAt);
        outboxRepository.save(outbox);
    }

    private String toJson(NotificationEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment notification payload", e);
        }
    }
}
