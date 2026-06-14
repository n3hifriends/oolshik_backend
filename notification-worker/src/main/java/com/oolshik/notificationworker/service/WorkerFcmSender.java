package com.oolshik.notificationworker.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.oolshik.notificationworker.config.NotificationWorkerProperties;
import com.oolshik.notificationworker.repo.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "notification.fcmEnabled", havingValue = "true", matchIfMissing = false)
public class WorkerFcmSender {

    private static final Logger log = LoggerFactory.getLogger(WorkerFcmSender.class);

    public record FcmMessage(String token, String title, String body, Map<String, Object> data) {}

    public record SendResult(boolean success, String error) {}

    private final UserDeviceRepository deviceRepository;
    private final NotificationWorkerProperties properties;

    public WorkerFcmSender(UserDeviceRepository deviceRepository, NotificationWorkerProperties properties) {
        this.deviceRepository = deviceRepository;
        this.properties = properties;
    }

    public Map<String, SendResult> sendMessages(List<FcmMessage> messages) {
        Map<String, SendResult> results = new LinkedHashMap<>();
        int batchSize = Math.max(1, properties.getFcmBatchSize());
        for (List<FcmMessage> batch : partition(messages, batchSize)) {
            processBatch(batch, results);
        }
        return results;
    }

    private void processBatch(List<FcmMessage> batch, Map<String, SendResult> results) {
        List<Message> fcmMessages = batch.stream()
                .map(m -> Message.builder()
                        .setToken(m.token())
                        .setNotification(Notification.builder()
                                .setTitle(m.title())
                                .setBody(m.body())
                                .build())
                        .putAllData(toStringMap(m.data()))
                        .build())
                .toList();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEach(fcmMessages);
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < batch.size(); i++) {
                String token = batch.get(i).token();
                SendResponse sr = responses.get(i);
                if (sr.isSuccessful()) {
                    results.put(token, new SendResult(true, null));
                } else {
                    FirebaseMessagingException ex = sr.getException();
                    MessagingErrorCode errorCode = ex != null ? ex.getMessagingErrorCode() : null;
                    String error = errorCode != null ? errorCode.name()
                            : (ex != null ? ex.getMessage() : "unknown");
                    results.put(token, new SendResult(false, error));
                    if (errorCode == MessagingErrorCode.UNREGISTERED) {
                        deactivateToken(token);
                    }
                }
            }
        } catch (FirebaseMessagingException ex) {
            log.warn("FCM sendEach batch failed size={}: {}", batch.size(), ex.getMessage());
            batch.forEach(m -> results.put(m.token(), new SendResult(false, ex.getMessage())));
        }
    }

    private void deactivateToken(String token) {
        try {
            deviceRepository.deactivateByTokenHash(sha256(token));
        } catch (Exception ex) {
            log.warn("Failed to deactivate FCM token: {}", ex.getMessage());
        }
    }

    private static Map<String, String> toStringMap(Map<String, Object> data) {
        Map<String, String> result = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((k, v) -> {
                if (v != null) result.put(k, String.valueOf(v));
            });
        }
        return result;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return value;
        }
    }
}
