package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.config.NotificationWorkerProperties;
import com.oolshik.notificationworker.entity.NotificationDeliveryLogEntity;
import com.oolshik.notificationworker.entity.UserDeviceEntity;
import com.oolshik.notificationworker.model.ExpoPushMessage;
import com.oolshik.notificationworker.model.ExpoPushResponse;
import com.oolshik.notificationworker.model.NotificationEventPayload;
import com.oolshik.notificationworker.model.NotificationEventType;
import com.oolshik.notificationworker.repo.HelpRequestCandidateRepository;
import com.oolshik.notificationworker.repo.NotificationDeliveryLogRepository;
import com.oolshik.notificationworker.repo.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final RecipientResolver recipientResolver;
    private final UserDeviceRepository userDeviceRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final HelpRequestCandidateRepository candidateRepository;
    private final NotificationTemplateService templateService;
    private final ExpoPushClient expoPushClient;
    private final NotificationWorkerProperties properties;

    public NotificationDispatcher(
            RecipientResolver recipientResolver,
            UserDeviceRepository userDeviceRepository,
            NotificationDeliveryLogRepository deliveryLogRepository,
            HelpRequestCandidateRepository candidateRepository,
            NotificationTemplateService templateService,
            ExpoPushClient expoPushClient,
            NotificationWorkerProperties properties
    ) {
        this.recipientResolver = recipientResolver;
        this.userDeviceRepository = userDeviceRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.candidateRepository = candidateRepository;
        this.templateService = templateService;
        this.expoPushClient = expoPushClient;
        this.properties = properties;
    }

    @Transactional
    public void dispatch(NotificationEventPayload payload) {
        List<UUID> recipients = recipientResolver.resolve(payload);
        if (recipients.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        Map<UUID, NotificationDeliveryLogEntity> logs = new HashMap<>();
        for (UUID recipientId : recipients) {
            String idempotencySeed = buildIdempotencySeed(payload, recipientId);
            String key = HashUtil.sha256(idempotencySeed + ":" + recipientId);
            NotificationDeliveryLogEntity existing = deliveryLogRepository.findByIdempotencyKey(key).orElse(null);
            if (existing != null) {
                if ("SENT".equals(existing.getStatus()) || "PROCESSING".equals(existing.getStatus())) {
                    continue;
                }
                deliveryLogRepository.updateStatus(existing.getId(), "PROCESSING", null, now);
                logs.put(recipientId, existing);
                continue;
            }
            NotificationDeliveryLogEntity logEntry = buildLog(payload, recipientId, key, now);
            try {
                deliveryLogRepository.save(logEntry);
                logs.put(recipientId, logEntry);
            } catch (DataIntegrityViolationException e) {
                // another worker inserted concurrently
            }
        }
        if (logs.isEmpty()) {
            return;
        }

        List<UserDeviceEntity> devices = userDeviceRepository.findActiveByUserIds(new ArrayList<>(logs.keySet()));
        Map<UUID, List<UserDeviceEntity>> devicesByUser = new HashMap<>();
        for (UserDeviceEntity device : devices) {
            devicesByUser.computeIfAbsent(device.getUserId(), k -> new ArrayList<>()).add(device);
        }

        List<OutgoingMessage> outgoing = new ArrayList<>();
        for (Map.Entry<UUID, NotificationDeliveryLogEntity> entry : logs.entrySet()) {
            UUID recipientId = entry.getKey();
            List<UserDeviceEntity> userDevices = devicesByUser.get(recipientId);
            if (userDevices == null || userDevices.isEmpty()) {
                deliveryLogRepository.updateStatus(entry.getValue().getId(), "FAILED", "no active tokens", now);
                continue;
            }
            NotificationTemplateService.NotificationTemplate template =
                    templateService.templateFor(payload.getEventType(), roleForRecipient(payload, recipientId));
            String body = enrichBodyWithOffer(template.body(), payload);
            for (UserDeviceEntity device : userDevices) {
                Map<String, Object> data = new HashMap<>();
                data.put("type", payload.getEventType());
                if (payload.getTaskId() != null) {
                    data.put("taskId", payload.getTaskId().toString());
                }
                if (payload.getPaymentRequestId() != null) {
                    data.put("paymentRequestId", payload.getPaymentRequestId().toString());
                    data.put("route", "PaymentPay");
                } else {
                    data.put("route", "TaskDetail");
                }
                if (payload.getOfferAmount() != null) {
                    data.put("offerAmount", payload.getOfferAmount().toPlainString());
                    data.put("offerCurrency", payload.getOfferCurrency() == null ? "INR" : payload.getOfferCurrency());
                }
                ExpoPushMessage message = new ExpoPushMessage();
                message.setTo(device.getToken());
                message.setTitle(template.title());
                message.setBody(body);
                message.setData(data);
                outgoing.add(new OutgoingMessage(recipientId, entry.getValue().getId(), device.getToken(), message));
            }
        }

        if (outgoing.isEmpty()) {
            return;
        }

        Map<UUID, DeliveryOutcome> outcomes = new HashMap<>();
        int batchSize = Math.max(1, properties.getExpoBatchSize());
        for (int i = 0; i < outgoing.size(); i += batchSize) {
            int end = Math.min(outgoing.size(), i + batchSize);
            List<OutgoingMessage> batch = outgoing.subList(i, end);
            List<ExpoPushMessage> messages = batch.stream().map(m -> m.message).toList();
            ExpoPushResponse response = sendWithRetries(messages);
            if (response == null) {
                log.warn("expo push batch failed size={}", batch.size());
                for (OutgoingMessage out : batch) {
                    outcomes.computeIfAbsent(out.recipientId, k -> new DeliveryOutcome())
                            .recordFailure("expo send failed");
                }
                continue;
            }
            if (response == null || response.getData() == null) {
                for (OutgoingMessage out : batch) {
                    outcomes.computeIfAbsent(out.recipientId, k -> new DeliveryOutcome())
                            .recordFailure("expo empty response");
                }
                continue;
            }
            List<ExpoPushResponse.ExpoPushTicket> tickets = response.getData();
            for (int j = 0; j < batch.size(); j++) {
                OutgoingMessage out = batch.get(j);
                ExpoPushResponse.ExpoPushTicket ticket = j < tickets.size() ? tickets.get(j) : null;
                if (ticket != null && "ok".equals(ticket.getStatus())) {
                    outcomes.computeIfAbsent(out.recipientId, k -> new DeliveryOutcome()).recordSuccess();
                } else {
                    String error = ticket == null ? "expo no ticket" : ticket.getMessage();
                    outcomes.computeIfAbsent(out.recipientId, k -> new DeliveryOutcome()).recordFailure(error);
                    maybeDeactivateToken(out, ticket);
                }
            }
        }

        List<UUID> notifiedRecipients = new ArrayList<>();
        for (Map.Entry<UUID, NotificationDeliveryLogEntity> entry : logs.entrySet()) {
            DeliveryOutcome outcome = outcomes.get(entry.getKey());
            if (outcome != null && outcome.hasSuccess()) {
                deliveryLogRepository.updateStatus(entry.getValue().getId(), "SENT", null, now);
                notifiedRecipients.add(entry.getKey());
            } else {
                String error = outcome == null ? "no delivery attempt" : outcome.firstError();
                deliveryLogRepository.updateStatus(entry.getValue().getId(), "FAILED", error, now);
            }
        }

        NotificationEventType type = NotificationEventType.valueOf(payload.getEventType());
        if (!notifiedRecipients.isEmpty() && (type == NotificationEventType.TASK_CREATED || type == NotificationEventType.TASK_RADIUS_EXPANDED)) {
            candidateRepository.updateStates(payload.getTaskId(), notifiedRecipients, "NOTIFIED");
        }
    }

    private String buildIdempotencySeed(NotificationEventPayload payload, UUID recipientId) {
        NotificationEventType type = NotificationEventType.valueOf(payload.getEventType());
        if (type == NotificationEventType.OFFER_UPDATED && payload.getTaskId() != null && payload.getOfferAmount() != null) {
            BigDecimal normalized = payload.getOfferAmount().stripTrailingZeros();
            String currency = payload.getOfferCurrency() == null ? "INR" : payload.getOfferCurrency();
            return type.name() + ":" + payload.getTaskId() + ":" + currency + ":" + normalized.toPlainString();
        }
        if (payload.getEventId() != null) {
            return payload.getEventId().toString();
        }
        return payload.getEventType() + ":" + payload.getTaskId() + ":" + recipientId;
    }

    private String enrichBodyWithOffer(String body, NotificationEventPayload payload) {
        if (payload.getOfferAmount() == null) {
            return body;
        }
        String currency = payload.getOfferCurrency() == null ? "INR" : payload.getOfferCurrency();
        String suffix = " Offer: " + currency + " " + payload.getOfferAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        if (body == null || body.isBlank()) {
            return suffix.trim();
        }
        if (body.contains("Offer:")) {
            return body;
        }
        return body + suffix;
    }

    private NotificationDeliveryLogEntity buildLog(
            NotificationEventPayload payload,
            UUID recipientId,
            String key,
            OffsetDateTime now
    ) {
        NotificationDeliveryLogEntity logEntry = new NotificationDeliveryLogEntity();
        logEntry.setId(UUID.randomUUID());
        logEntry.setIdempotencyKey(key);
        logEntry.setEventId(payload.getEventId());
        logEntry.setRecipientUserId(recipientId);
        logEntry.setProvider("EXPO");
        logEntry.setStatus("PROCESSING");
        logEntry.setCreatedAt(now);
        logEntry.setUpdatedAt(now);
        return logEntry;
    }

    private void maybeDeactivateToken(OutgoingMessage out, ExpoPushResponse.ExpoPushTicket ticket) {
        if (ticket == null || ticket.getDetails() == null) {
            return;
        }
        Object error = ticket.getDetails().get("error");
        if (error == null) {
            return;
        }
        String errorText = String.valueOf(error);
        if ("DeviceNotRegistered".equals(errorText) || "InvalidCredentials".equals(errorText)) {
            String tokenHash = HashUtil.sha256(out.token);
            userDeviceRepository.deactivateByTokenHash(tokenHash);
        }
    }

    private ExpoPushResponse sendWithRetries(List<ExpoPushMessage> messages) {
        int maxAttempts = Math.max(1, properties.getMaxSendAttempts());
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return expoPushClient.send(messages);
            } catch (Exception ex) {
                lastError = ex;
            }
        }
        if (lastError != null) {
            log.warn("expo push failed after retries");
        }
        return null;
    }

    private NotificationTemplateService.RecipientRole roleForRecipient(NotificationEventPayload payload, UUID recipientId) {
        if (payload.getRequesterUserId() != null && payload.getRequesterUserId().equals(recipientId)) {
            return NotificationTemplateService.RecipientRole.REQUESTER;
        }
        return NotificationTemplateService.RecipientRole.HELPER;
    }

    private static class OutgoingMessage {
        private final UUID recipientId;
        private final UUID logId;
        private final String token;
        private final ExpoPushMessage message;

        private OutgoingMessage(UUID recipientId, UUID logId, String token, ExpoPushMessage message) {
            this.recipientId = recipientId;
            this.logId = logId;
            this.token = token;
            this.message = message;
        }
    }

    private static class DeliveryOutcome {
        private boolean success;
        private String firstError;

        void recordSuccess() {
            success = true;
        }

        void recordFailure(String error) {
            if (firstError == null) {
                firstError = error;
            }
        }

        boolean hasSuccess() {
            return success;
        }

        String firstError() {
            return firstError;
        }
    }
}
