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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.messaging.kafka.enabled", havingValue = "true")
public class NotificationOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxPublisher.class);
    private static final int SEND_TIMEOUT_SECONDS = 5;
    private static final long CLAIM_LEASE_BUFFER_SECONDS = 30;

    private final NotificationOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties topics;
    private final NotificationProperties properties;
    private final TransactionTemplate transactionTemplate;

    public NotificationOutboxPublisher(
            NotificationOutboxRepository outboxRepository,
            @Qualifier("notificationKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicProperties topics,
            NotificationProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Runs with no open DB transaction: claimBatch()/finalizeStatus() each take their own
    // short-lived transaction, so the blocking Kafka send below never holds a Hikari
    // connection or a FOR UPDATE row lock while waiting on the network.
    @Scheduled(fixedDelayString = "${app.notification.outboxPublishIntervalMs:2000}")
    public void publishPending() {
        for (NotificationOutboxEntity outbox : claimBatch()) {
            publishOne(outbox);
        }
    }

    private List<NotificationOutboxEntity> claimBatch() {
        return transactionTemplate.execute(txStatus -> {
            OffsetDateTime now = OffsetDateTime.now();
            List<NotificationOutboxEntity> batch = outboxRepository.lockNextBatch(
                    List.of(NotificationOutboxStatus.PENDING.name(), NotificationOutboxStatus.FAILED.name()),
                    now,
                    properties.getOutboxBatchSize()
            );
            // Lease the claimed rows past the worst-case send time for this batch so a crash
            // between claim and finalize self-heals once the lease expires, instead of
            // needing a separate reaper.
            OffsetDateTime lease = now.plusSeconds(
                    (long) properties.getOutboxBatchSize() * SEND_TIMEOUT_SECONDS + CLAIM_LEASE_BUFFER_SECONDS
            );
            for (NotificationOutboxEntity outbox : batch) {
                outboxRepository.updateStatus(
                        outbox.getId(),
                        outbox.getStatus(),
                        outbox.getAttemptCount(),
                        lease,
                        outbox.getLastError(),
                        now
                );
            }
            return batch;
        });
    }

    private void publishOne(NotificationOutboxEntity outbox) {
        OffsetDateTime now = OffsetDateTime.now();
        try {
            kafkaTemplate
                    .send(topics.getNotificationEvents(), outbox.getId().toString(), outbox.getPayloadJson())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            finalizeStatus(outbox.getId(), NotificationOutboxStatus.PUBLISHED.name(), outbox.getAttemptCount() + 1, now, null);
        } catch (Exception ex) {
            int attempts = outbox.getAttemptCount() + 1;
            NotificationOutboxStatus status = attempts >= properties.getOutboxMaxAttempts()
                    ? NotificationOutboxStatus.DEAD
                    : NotificationOutboxStatus.FAILED;
            OffsetDateTime nextAttempt = attempts >= properties.getOutboxMaxAttempts()
                    ? now
                    : now.plusSeconds(backoffSeconds(attempts));
            finalizeStatus(outbox.getId(), status.name(), attempts, nextAttempt, safeMessage(ex));
            if (status == NotificationOutboxStatus.DEAD) {
                log.error("notification outbox dead id={} attempts={}", outbox.getId(), attempts);
            } else {
                log.warn("notification outbox publish failed id={} attempts={}", outbox.getId(), attempts);
            }
        }
    }

    private void finalizeStatus(UUID id, String status, int attemptCount, OffsetDateTime nextAttemptAt, String lastError) {
        transactionTemplate.execute(txStatus -> outboxRepository.updateStatus(
                id, status, attemptCount, nextAttemptAt, lastError, OffsetDateTime.now()
        ));
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
