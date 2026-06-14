package com.oolshik.backend.admin;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.oolshik.backend.repo.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.admin.notification.pushProvider",
        havingValue = "FCM"
)
public class AdminFcmSender implements AdminPushSender {

    private static final Logger log = LoggerFactory.getLogger(AdminFcmSender.class);
    private static final int FCM_BATCH_LIMIT = 500;

    private final UserDeviceRepository deviceRepository;

    public AdminFcmSender(UserDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public String provider() {
        return "FCM";
    }

    private void initializeFirebase() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build();
        FirebaseApp.initializeApp(options);
    }

    @Override
    public Map<String, SendResult> sendBatch(List<String> tokens, String title, String body, UUID broadcastId,
                                             String routeKey, String routeTargetId) {
        Map<String, SendResult> results = new LinkedHashMap<>();
        for (List<String> batch : partition(tokens, FCM_BATCH_LIMIT)) {
            processBatch(batch, title, body, broadcastId, routeKey, routeTargetId, results);
        }
        return results;
    }

    private void processBatch(List<String> tokens, String title, String body, UUID broadcastId,
                              String routeKey, String routeTargetId, Map<String, SendResult> results) {
        String effectiveRoute = (routeKey != null && !routeKey.isBlank()) ? routeKey : "InAppInbox";
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("type", "ADMIN_BROADCAST")
                .putData("route", effectiveRoute)
                .putData("broadcastId", broadcastId != null ? broadcastId.toString() : "");
        if (routeTargetId != null && !routeTargetId.isBlank()) {
            builder.putData("taskId", routeTargetId);
        }
        MulticastMessage message = builder.build();
        try {
            initializeFirebase();
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
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
            log.warn("FCM multicast batch failed: {}", ex.getMessage());
            tokens.forEach(t -> results.put(t, new SendResult(false, ex.getMessage())));
        } catch (IOException ex) {
            log.warn("FCM initialization failed: {}", ex.getMessage());
            tokens.forEach(t -> results.put(t, new SendResult(false, "FCM initialization failed: " + ex.getMessage())));
        }
    }

    private void deactivateToken(String token) {
        try {
            deviceRepository.findByTokenHash(sha256(token)).ifPresent(device -> {
                device.setActive(false);
                deviceRepository.save(device);
            });
        } catch (Exception ex) {
            log.warn("Failed to deactivate FCM token: {}", ex.getMessage());
        }
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
