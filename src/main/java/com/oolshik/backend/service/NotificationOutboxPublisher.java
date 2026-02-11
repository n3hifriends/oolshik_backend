package com.oolshik.backend.service;

import com.oolshik.backend.config.KafkaTopicProperties;
import com.oolshik.backend.config.NotificationProperties;
import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.notification.NotificationOutboxStatus;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxPublisher.class);

    private final NotificationOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties topics;
    private final NotificationProperties properties;

    public NotificationOutboxPublisher(
            NotificationOutboxRepository outboxRepository,
            @Qualifier("notificationKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicProperties topics,
            NotificationProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.notification.outboxPublishIntervalMs:2000}")
    @Transactional
    public void publishPending() {
        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationOutboxEntity> batch = outboxRepository.lockNextBatch(
                List.of(NotificationOutboxStatus.PENDING.name(), NotificationOutboxStatus.FAILED.name()),
                now,
                properties.getOutboxBatchSize()
        );
        for (NotificationOutboxEntity outbox : batch) {
            try {
                kafkaTemplate
                        .send(topics.getNotificationEvents(), outbox.getId().toString(), outbox.getPayloadJson())
                        .get(5, TimeUnit.SECONDS);
                outboxRepository.updateStatus(
                        outbox.getId(),
                        NotificationOutboxStatus.PUBLISHED.name(),
                        outbox.getAttemptCount() + 1,
                        now,
                        null,
                        now
                );
            } catch (Exception ex) {
                int attempts = outbox.getAttemptCount() + 1;
                NotificationOutboxStatus status = attempts >= properties.getOutboxMaxAttempts()
                        ? NotificationOutboxStatus.DEAD
                        : NotificationOutboxStatus.FAILED;
                OffsetDateTime nextAttempt = attempts >= properties.getOutboxMaxAttempts()
                        ? now
                        : now.plusSeconds(backoffSeconds(attempts));
                outboxRepository.updateStatus(
                        outbox.getId(),
                        status.name(),
                        attempts,
                        nextAttempt,
                        safeMessage(ex),
                        now
                );
                if (status == NotificationOutboxStatus.DEAD) {
                    log.error("notification outbox dead id={} attempts={}", outbox.getId(), attempts);
                } else {
                    log.warn("notification outbox publish failed id={} attempts={}", outbox.getId(), attempts);
                }
            }
        }
    }

    private long backoffSeconds(int attempt) {
        long base = Math.min(60, (long) Math.pow(2, attempt));
        return Math.max(1, base);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }
}
