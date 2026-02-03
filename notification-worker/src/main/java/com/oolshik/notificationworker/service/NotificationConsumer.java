package com.oolshik.notificationworker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.notificationworker.model.NotificationEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ObjectMapper objectMapper;
    private final LegacyEventMapper legacyEventMapper;
    private final NotificationCoalescer coalescer;

    public NotificationConsumer(
            ObjectMapper objectMapper,
            LegacyEventMapper legacyEventMapper,
            NotificationCoalescer coalescer
    ) {
        this.objectMapper = objectMapper;
        this.legacyEventMapper = legacyEventMapper;
        this.coalescer = coalescer;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.notificationEvents}",
            containerFactory = "notificationKafkaListenerContainerFactory"
    )
    public void onMessage(String payloadJson) {
        NotificationEventPayload payload;
        try {
            payload = objectMapper.readValue(payloadJson, NotificationEventPayload.class);
        } catch (Exception ex) {
            log.warn("failed to parse notification event");
            return;
        }
        NotificationEventPayload mapped = legacyEventMapper.mapIfLegacy(payload);
        if (mapped == null) {
            return;
        }
        coalescer.enqueue(mapped);
    }
}
