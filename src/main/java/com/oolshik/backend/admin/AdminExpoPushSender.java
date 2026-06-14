package com.oolshik.backend.admin;

import com.oolshik.backend.repo.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.admin.notification.pushProvider",
        havingValue = "EXPO"
)
public class AdminExpoPushSender implements AdminPushSender {

    private static final Logger log = LoggerFactory.getLogger(AdminExpoPushSender.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final int EXPO_BATCH_LIMIT = 100;

    private final RestTemplate restTemplate;
    private final UserDeviceRepository deviceRepository;

    public AdminExpoPushSender(RestTemplateBuilder builder, UserDeviceRepository deviceRepository) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.deviceRepository = deviceRepository;
    }

    @Override
    public String provider() {
        return "EXPO";
    }

    @Override
    public Map<String, SendResult> sendBatch(List<String> tokens, String title, String body, UUID broadcastId,
                                             String routeKey, String routeTargetId) {
        Map<String, SendResult> results = new LinkedHashMap<>();
        for (List<String> batch : partition(tokens, EXPO_BATCH_LIMIT)) {
            List<Map<String, Object>> messages = batch.stream()
                    .map(token -> buildMessage(token, title, body, broadcastId, routeKey, routeTargetId))
                    .toList();
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        EXPO_PUSH_URL,
                        new HttpEntity<>(messages, buildHeaders()),
                        Map.class
                );
                parseResponse(response.getBody(), batch, results);
            } catch (Exception ex) {
                log.warn("Expo push batch failed: {}", ex.getMessage());
                batch.forEach(t -> results.put(t, new SendResult(false, ex.getMessage())));
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private void parseResponse(Map<?, ?> responseBody, List<String> tokens, Map<String, SendResult> results) {
        if (responseBody == null) {
            tokens.forEach(t -> results.put(t, new SendResult(false, "null response")));
            return;
        }
        List<?> data = (List<?>) responseBody.get("data");
        if (data == null || data.size() != tokens.size()) {
            tokens.forEach(t -> results.put(t, new SendResult(false, "unexpected response shape")));
            return;
        }
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            Map<String, Object> item = (Map<String, Object>) data.get(i);
            String status = (String) item.get("status");
            if ("ok".equals(status)) {
                results.put(token, new SendResult(true, null));
            } else {
                Map<String, Object> details = (Map<String, Object>) item.get("details");
                String error = details != null ? (String) details.get("error") : "error";
                results.put(token, new SendResult(false, error));
                if ("DeviceNotRegistered".equals(error)) {
                    deactivateToken(token);
                }
            }
        }
    }

    private void deactivateToken(String token) {
        try {
            deviceRepository.findByTokenHash(hashToken(token)).ifPresent(device -> {
                device.setActive(false);
                deviceRepository.save(device);
            });
        } catch (Exception ex) {
            log.warn("Failed to deactivate Expo push token: {}", ex.getMessage());
        }
    }

    private Map<String, Object> buildMessage(String token, String title, String body, UUID broadcastId,
                                             String routeKey, String routeTargetId) {
        String effectiveRoute = (routeKey != null && !routeKey.isBlank()) ? routeKey : "InAppInbox";
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("to", token);
        msg.put("title", title);
        msg.put("body", body);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "ADMIN_BROADCAST");
        data.put("route", effectiveRoute);
        data.put("broadcastId", broadcastId != null ? broadcastId.toString() : "");
        if (routeTargetId != null && !routeTargetId.isBlank()) {
            data.put("taskId", routeTargetId);
        }
        msg.put("data", data);
        return msg;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        return headers;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return token;
        }
    }
}
