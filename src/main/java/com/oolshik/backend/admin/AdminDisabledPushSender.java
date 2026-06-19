package com.oolshik.backend.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.admin.notification.pushProvider",
        havingValue = "DISABLED"
)
public class AdminDisabledPushSender implements AdminPushSender {

    @Override
    public String provider() {
        return "DISABLED";
    }

    @Override
    public Map<String, SendResult> sendBatch(List<String> tokens, String title, String body, UUID broadcastId,
                                             String routeKey, String routeTargetId) {
        Map<String, SendResult> results = new LinkedHashMap<>();
        tokens.forEach(token -> results.put(token, new SendResult(false, "push provider disabled")));
        return results;
    }
}
