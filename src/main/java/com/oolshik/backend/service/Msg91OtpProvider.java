package com.oolshik.backend.service;

import com.oolshik.backend.config.OtpProperties;
import com.oolshik.backend.util.PhoneUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "msg91")
public class Msg91OtpProvider implements OtpProvider {

    private final RestTemplate restTemplate;
    private final OtpProperties.Msg91 properties;

    public Msg91OtpProvider(RestTemplateBuilder restTemplateBuilder, OtpProperties otpProperties) {
        this(
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(5))
                        .setReadTimeout(Duration.ofSeconds(10))
                        .build(),
                otpProperties
        );
    }

    Msg91OtpProvider(RestTemplate restTemplate, OtpProperties otpProperties) {
        this.properties = otpProperties.getMsg91();
        this.restTemplate = restTemplate;
        validateConfiguration();
    }

    @Override
    public String providerId() {
        return "msg91";
    }

    @Override
    public void sendOtp(String phone, String message) throws OtpDeliveryException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", properties.getApiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mobiles", normalizeMsg91Phone(phone));
        body.put("sender", properties.getSenderId());
        body.put("route", "4");
        body.put("message", message);
        body.put("template_id", properties.getTemplateId());
        body.put("entity_id", properties.getEntityId());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    properties.getBaseUrl(),
                    new HttpEntity<>(body, headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new OtpDeliveryException("Failed to deliver OTP via MSG91");
            }
        } catch (RestClientException ex) {
            throw new OtpDeliveryException("Failed to deliver OTP via MSG91", ex);
        }
    }

    private String normalizeMsg91Phone(String phone) {
        String normalized = PhoneUtil.normalize(phone);
        return normalized != null && normalized.startsWith("+")
                ? normalized.substring(1)
                : normalized;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("APP_OTP_MSG91_API_KEY is required when app.otp.provider=msg91");
        }
        if (!StringUtils.hasText(properties.getTemplateId())) {
            throw new IllegalStateException("APP_OTP_MSG91_TEMPLATE_ID is required when app.otp.provider=msg91");
        }
        if (!StringUtils.hasText(properties.getSenderId())) {
            throw new IllegalStateException("APP_OTP_MSG91_SENDER_ID is required when app.otp.provider=msg91");
        }
        if (!StringUtils.hasText(properties.getEntityId())) {
            throw new IllegalStateException("APP_OTP_MSG91_ENTITY_ID is required when app.otp.provider=msg91");
        }
    }
}
