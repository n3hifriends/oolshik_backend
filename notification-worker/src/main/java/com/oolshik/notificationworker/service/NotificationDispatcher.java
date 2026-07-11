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
import java.util.Optional;
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
    private final Optional<WorkerFcmSender> fcmSender;
    private final NotificationWorkerProperties properties;

    public NotificationDispatcher(
            RecipientResolver recipientResolver,
            UserDeviceRepository userDeviceRepository,
            NotificationDeliveryLogRepository deliveryLogRepository,
            HelpRequestCandidateRepository candidateRepository,
            NotificationTemplateService templateService,
            ExpoPushClient expoPushClient,
            Optional<WorkerFcmSender> fcmSender,
            NotificationWorkerProperties properties
    ) {
        this.recipientResolver = recipientResolver;
        this.userDeviceRepository = userDeviceRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.candidateRepository = candidateRepository;
        this.templateService = templateService;
        this.expoPushClient = expoPushClient;
        this.fcmSender = fcmSender;
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

        List<UserDeviceEntity> allDevices = userDeviceRepository.findActiveByUserIds(new ArrayList<>(logs.keySet()));
        Map<UUID, List<UserDeviceEntity>> devicesByUser = new HashMap<>();
        for (UserDeviceEntity device : allDevices) {
            devicesByUser.computeIfAbsent(device.getUserId(), k -> new ArrayList<>()).add(device);
        }
        Map<UUID, String> localesByUser = new HashMap<>();
        for (UserDeviceRepository.UserLocaleRow row : userDeviceRepository.findPreferredLocalesByUserIds(
                new ArrayList<>(logs.keySet()))) {
            localesByUser.put(row.getUserId(), LocaleSupport.normalizeTag(row.getPreferredLanguage()));
        }

        List<ExpoOutgoingMessage> expoOutgoing = new ArrayList<>();
        List<WorkerFcmSender.FcmMessage> fcmOutgoing = new ArrayList<>();
        Map<String, UUID> tokenToRecipient = new HashMap<>();
        Map<UUID, DeliveryOutcome> outcomes = new HashMap<>();

        for (Map.Entry<UUID, NotificationDeliveryLogEntity> entry : logs.entrySet()) {
            UUID recipientId = entry.getKey();
            List<UserDeviceEntity> userDevices = devicesByUser.get(recipientId);
            if (userDevices == null || userDevices.isEmpty()) {
                deliveryLogRepository.updateStatusAndProvider(
                        entry.getValue().getId(), "FAILED", "EXPO", "no active tokens", now);
                continue;
            }
            String localeTag = localesByUser.getOrDefault(recipientId, LocaleSupport.EN_IN_TAG);
            NotificationTemplateService.NotificationTemplate template =
                    templateService.templateFor(payload.getEventType(), roleForRecipient(payload, recipientId), localeTag);
            String body = enrichBodyWithOffer(template.body(), payload, localeTag);
            Map<String, Object> data = buildDataMap(payload);

            for (UserDeviceEntity device : userDevices) {
                String provider = device.getProvider();
                if ("FCM".equals(provider)) {
                    if (!fcmSender.isPresent()) {
                        outcomes.computeIfAbsent(recipientId, k -> new DeliveryOutcome())
                                .recordFailure("FCM provider disabled", "FCM");
                        continue;
                    }
                    tokenToRecipient.put(device.getToken(), recipientId);
                    fcmOutgoing.add(new WorkerFcmSender.FcmMessage(
                            device.getToken(), template.title(), body, data));
                } else if ("EXPO".equals(provider)) {
                    ExpoPushMessage message = new ExpoPushMessage();
                    message.setTo(device.getToken());
                    message.setTitle(template.title());
                    message.setBody(body);
                    message.setData(data);
                    expoOutgoing.add(new ExpoOutgoingMessage(
                            recipientId, entry.getValue().getId(), device.getToken(), message));
                } else {
                    outcomes.computeIfAbsent(recipientId, k -> new DeliveryOutcome())
                            .recordFailure("unsupported push provider: " + provider, provider);
                }
            }
        }

        if (!fcmOutgoing.isEmpty() && fcmSender.isPresent()) {
            Map<String, WorkerFcmSender.SendResult> fcmResults = fcmSender.get().sendMessages(fcmOutgoing);
            for (WorkerFcmSender.FcmMessage out : fcmOutgoing) {
                UUID recipientId = tokenToRecipient.get(out.token());
                WorkerFcmSender.SendResult result = fcmResults.get(out.token());
                if (result != null && result.success()) {
                    outcomes.computeIfAbsent(recipientId, k -> new DeliveryOutcome()).recordSuccess("FCM");
                } else {
                    String error = result != null ? result.error() : "fcm no response";
                    outcomes.computeIfAbsent(recipientId, k -> new DeliveryOutcome()).recordFailure(error, "FCM");
                }
            }
        }

        if (!expoOutgoing.isEmpty()) {
            int batchSize = Math.max(1, properties.getExpoBatchSize());
            for (int i = 0; i < expoOutgoing.size(); i += batchSize) {
                int end = Math.min(expoOutgoing.size(), i + batchSize);
                List<ExpoOutgoingMessage> batch = expoOutgoing.subList(i, end);
                List<ExpoPushMessage> messages = batch.stream().map(m -> m.message).toList();
                ExpoPushResponse response = sendWithRetries(messages);
                if (response == null || response.getData() == null) {
                    log.warn("expo push batch failed size={}", batch.size());
                    for (ExpoOutgoingMessage out : batch) {
                        outcomes.computeIfAbsent(out.recipientId, k -> new DeliveryOutcome())
                                .recordFailure("expo send failed", "EXPO");
                    }
                    continue;
                }
                List<ExpoPushResponse.ExpoPushTicket> tickets = response.getData();
                for (int j = 0; j < batch.size(); j++) {
                    ExpoOutgoingMessage out = batch.get(j);
                    ExpoPushResponse.ExpoPushTicket ticket = j < tickets.size() ? tickets.get(j) : null;
                    if (ticket != null && "ok".equals(ticket.getStatus())) {
                        outcomes.computeIfAbsent(out.recipientId, k -> new DeliveryOutcome()).recordSuccess("EXPO");
                    } else {
                        String error = ticket == null ? "expo no ticket" : ticket.getMessage();
                        outcomes.computeIfAbsent(out.recipientId, k -> new DeliveryOutcome())
                                .recordFailure(error, "EXPO");
                        maybeDeactivateToken(out, ticket);
                    }
                }
            }
        }

        List<UUID> notifiedRecipients = new ArrayList<>();
        for (Map.Entry<UUID, NotificationDeliveryLogEntity> entry : logs.entrySet()) {
            UUID recipientId = entry.getKey();
            DeliveryOutcome outcome = outcomes.get(recipientId);
            if (outcome != null && outcome.hasSuccess()) {
                deliveryLogRepository.updateStatusAndProvider(
                        entry.getValue().getId(), "SENT", outcome.provider(), null, now);
                notifiedRecipients.add(recipientId);
            } else {
                String error = outcome == null ? "no delivery attempt" : outcome.firstError();
                String provider = outcome != null ? outcome.provider() : "EXPO";
                deliveryLogRepository.updateStatusAndProvider(
                        entry.getValue().getId(), "FAILED", provider, error, now);
            }
        }

        NotificationEventType type = NotificationEventType.valueOf(payload.getEventType());
        if (!notifiedRecipients.isEmpty() && (type == NotificationEventType.TASK_CREATED || type == NotificationEventType.TASK_RADIUS_EXPANDED)) {
            candidateRepository.updateStates(payload.getTaskId(), notifiedRecipients, "NOTIFIED");
        }
    }

    private Map<String, Object> buildDataMap(NotificationEventPayload payload) {
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
        return data;
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

    private String enrichBodyWithOffer(String body, NotificationEventPayload payload, String localeTag) {
        if (payload.getOfferAmount() == null) {
            return body;
        }
        String currency = payload.getOfferCurrency() == null ? "INR" : payload.getOfferCurrency();
        String amount = payload.getOfferAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String offerLabel = LocaleSupport.isMarathi(localeTag) ? "ऑफर" : "Offer";
        String formatted = "INR".equals(currency) ? "₹" + amount : currency + " " + amount;
        String suffix = offerLabel + ": " + formatted;
        if (body == null || body.isBlank()) {
            return suffix;
        }
        if (body.contains(offerLabel + ":")) {
            return body;
        }
        return body + " " + suffix;
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
        logEntry.setProvider("PENDING");
        logEntry.setStatus("PROCESSING");
        logEntry.setCreatedAt(now);
        logEntry.setUpdatedAt(now);
        return logEntry;
    }

    private void maybeDeactivateToken(ExpoOutgoingMessage out, ExpoPushResponse.ExpoPushTicket ticket) {
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
        NotificationEventType type = NotificationEventType.valueOf(payload.getEventType());
        return switch (type) {
            case TASK_CREATED, TASK_RADIUS_EXPANDED, OFFER_UPDATED ->
                    NotificationTemplateService.RecipientRole.CANDIDATE_HELPER;
            case PAYMENT_ACTION_REQUIRED ->
                    NotificationTemplateService.RecipientRole.PAYER;
            case PAYMENT_INITIATED, PAYMENT_MARKED_PAID ->
                    payload.getPayerUserId() != null && payload.getPayerUserId().equals(recipientId)
                            ? NotificationTemplateService.RecipientRole.PAYER
                            : NotificationTemplateService.RecipientRole.PAYEE;
            case PAYMENT_REQUEST_CREATED -> {
                boolean isPayer = payload.getPayerUserId() != null
                        ? payload.getPayerUserId().equals(recipientId)
                        : payload.getRequesterUserId() != null && payload.getRequesterUserId().equals(recipientId);
                yield isPayer ? NotificationTemplateService.RecipientRole.PAYER
                             : NotificationTemplateService.RecipientRole.PAYEE;
            }
            case PAYMENT_EXPIRED -> {
                boolean isPayer = payload.getPayerUserId() != null
                        ? payload.getPayerUserId().equals(recipientId)
                        : payload.getRequesterUserId() != null && payload.getRequesterUserId().equals(recipientId);
                yield isPayer ? NotificationTemplateService.RecipientRole.PAYER
                             : NotificationTemplateService.RecipientRole.REQUESTER;
            }
            default ->
                    payload.getRequesterUserId() != null && payload.getRequesterUserId().equals(recipientId)
                            ? NotificationTemplateService.RecipientRole.REQUESTER
                            : NotificationTemplateService.RecipientRole.HELPER;
        };
    }

    private static class ExpoOutgoingMessage {
        private final UUID recipientId;
        private final UUID logId;
        private final String token;
        private final ExpoPushMessage message;

        private ExpoOutgoingMessage(UUID recipientId, UUID logId, String token, ExpoPushMessage message) {
            this.recipientId = recipientId;
            this.logId = logId;
            this.token = token;
            this.message = message;
        }
    }

    private static class DeliveryOutcome {
        private boolean success;
        private String firstError;
        private String provider;

        void recordSuccess(String provider) {
            success = true;
            this.provider = provider;
        }

        void recordFailure(String error, String provider) {
            if (firstError == null) {
                firstError = error;
                this.provider = provider;
            }
        }

        boolean hasSuccess() {
            return success;
        }

        String firstError() {
            return firstError;
        }

        String provider() {
            return provider != null ? provider : "EXPO";
        }
    }
}
