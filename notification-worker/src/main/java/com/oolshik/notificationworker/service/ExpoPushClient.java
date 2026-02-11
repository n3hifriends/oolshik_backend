package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.config.NotificationWorkerProperties;
import com.oolshik.notificationworker.model.ExpoPushMessage;
import com.oolshik.notificationworker.model.ExpoPushResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ExpoPushClient {

    private final RestTemplate restTemplate;
    private final NotificationWorkerProperties properties;

    public ExpoPushClient(RestTemplateBuilder restTemplateBuilder, NotificationWorkerProperties properties) {
        this.restTemplate = restTemplateBuilder.build();
        this.properties = properties;
    }

    public ExpoPushResponse send(List<ExpoPushMessage> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<ExpoPushMessage>> request = new HttpEntity<>(messages, headers);
        ResponseEntity<ExpoPushResponse> response = restTemplate.postForEntity(
                properties.getExpoEndpoint(),
                request,
                ExpoPushResponse.class
        );
        return response.getBody();
    }
}
