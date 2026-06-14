package com.oolshik.backend.admin;

import com.oolshik.backend.config.AdminNotificationProperties;
import com.oolshik.backend.util.PhoneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.admin.notification.sms.enabled", havingValue = "true")
public class Msg91AdminSmsSender implements AdminSmsSender {

    private static final Logger log = LoggerFactory.getLogger(Msg91AdminSmsSender.class);

    private final RestTemplate restTemplate;
    private final AdminNotificationProperties.Sms smsProps;

    public Msg91AdminSmsSender(RestTemplateBuilder builder, AdminNotificationProperties props) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.smsProps = props.getSms();
    }

    @Override
    public void send(String phoneE164, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", smsProps.getMsg91ApiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mobiles", normalizePhone(phoneE164));
        body.put("sender", smsProps.getMsg91SenderId());
        body.put("route", "4");
        body.put("message", text);

        try {
            restTemplate.postForEntity(smsProps.getMsg91BaseUrl(), new HttpEntity<>(body, headers), String.class);
        } catch (Exception ex) {
            log.warn("Admin SMS send failed to {}: {}", phoneE164, ex.getMessage());
            throw new RuntimeException("SMS delivery failed: " + ex.getMessage(), ex);
        }
    }

    private String normalizePhone(String phone) {
        String normalized = PhoneUtil.normalize(phone);
        return normalized != null && normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }
}
