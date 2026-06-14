package com.oolshik.backend.admin;

import com.oolshik.backend.config.AdminNotificationProperties;
import com.oolshik.backend.entity.AdminBroadcastDeliveryEntity;
import com.oolshik.backend.entity.AdminBroadcastEntity;
import com.oolshik.backend.entity.UserDeviceEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.entity.UserNotificationEntity;
import com.oolshik.backend.repo.AdminBroadcastDeliveryRepository;
import com.oolshik.backend.repo.AdminBroadcastRepository;
import com.oolshik.backend.repo.UserDeviceRepository;
import com.oolshik.backend.repo.UserNotificationRepository;
import com.oolshik.backend.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AdminBroadcastScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdminBroadcastScheduler.class);

    private final AdminBroadcastRepository broadcastRepository;
    private final AdminBroadcastDeliveryRepository deliveryRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final UserDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final AdminNotificationService notificationService;
    private final AdminPushSender pushSender;
    private final Optional<AdminSmsSender> smsSender;
    private final AdminNotificationProperties props;
    private final TransactionTemplate transactionTemplate;

    public AdminBroadcastScheduler(
            AdminBroadcastRepository broadcastRepository,
            AdminBroadcastDeliveryRepository deliveryRepository,
            UserNotificationRepository userNotificationRepository,
            UserDeviceRepository deviceRepository,
            UserRepository userRepository,
            AdminNotificationService notificationService,
            AdminPushSender pushSender,
            Optional<AdminSmsSender> smsSender,
            AdminNotificationProperties props,
            TransactionTemplate transactionTemplate
    ) {
        this.broadcastRepository = broadcastRepository;
        this.deliveryRepository = deliveryRepository;
        this.userNotificationRepository = userNotificationRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.pushSender = pushSender;
        this.smsSender = smsSender;
        this.props = props;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${app.admin.notification.schedulerIntervalMs:5000}")
    public void processNextQueued() {
        Optional<AdminBroadcastEntity> claimedBroadcast = claimNextBroadcast();
        if (claimedBroadcast.isEmpty()) {
            return;
        }
        AdminBroadcastEntity broadcast = claimedBroadcast.get();
        UUID broadcastId = broadcast.getId();

        log.info("Processing broadcast id={} targetType={} channels={}", broadcastId, broadcast.getTargetType(), broadcast.getChannels());

        List<UUID> recipientIds;
        try {
            recipientIds = notificationService.resolveRecipientIds(broadcast.getTargetType(), broadcast.getTargetValue());
        } catch (Exception ex) {
            log.error("Failed to resolve recipients for broadcast {}: {}", broadcastId, ex.getMessage());
            updateCompletion(broadcastId, "PARTIAL_FAILURE", 0, 0, 0, 0, 0, 0);
            return;
        }

        List<String> channels = Arrays.asList(broadcast.getChannels().split(","));
        int pushSent = 0, pushFailed = 0, smsSent = 0, smsFailed = 0, inAppCreated = 0;

        for (List<UUID> batch : partition(recipientIds, props.getBatchSize())) {
            if (channels.contains("IN_APP")) {
                inAppCreated += processInApp(broadcast, batch);
            }
            if (channels.contains("PUSH")) {
                int[] counts = processPush(broadcast, batch);
                pushSent += counts[0];
                pushFailed += counts[1];
            }
            if (channels.contains("SMS")) {
                int[] counts = processSms(broadcast, batch);
                smsSent += counts[0];
                smsFailed += counts[1];
            }
        }

        boolean anyFailures = pushFailed > 0 || smsFailed > 0;
        String finalStatus = anyFailures ? "PARTIAL_FAILURE" : "COMPLETED";

        updateCompletion(broadcastId, finalStatus, recipientIds.size(), pushSent, pushFailed, smsSent, smsFailed, inAppCreated);

        log.info("Broadcast {} done: status={} recipients={} pushSent={} pushFailed={} smsSent={} smsFailed={} inApp={}",
                broadcastId, finalStatus, recipientIds.size(), pushSent, pushFailed, smsSent, smsFailed, inAppCreated);
    }

    private Optional<AdminBroadcastEntity> claimNextBroadcast() {
        Optional<AdminBroadcastEntity> broadcast = transactionTemplate.execute(status -> {
            resetStaleProcessing();

            Optional<UUID> nextId = broadcastRepository.lockNextQueued();
            if (nextId.isEmpty()) {
                return Optional.empty();
            }

            UUID broadcastId = nextId.get();
            int claimed = broadcastRepository.claimById(broadcastId, OffsetDateTime.now());
            if (claimed == 0) {
                return Optional.empty();
            }

            Optional<AdminBroadcastEntity> claimedBroadcast = broadcastRepository.findById(broadcastId);
            if (claimedBroadcast.isEmpty()) {
                log.warn("Claimed broadcast {} not found", broadcastId);
            }
            return claimedBroadcast;
        });
        return broadcast != null ? broadcast : Optional.empty();
    }

    private void updateCompletion(UUID broadcastId, String status, int totalRecipients,
                                  int pushSent, int pushFailed, int smsSent, int smsFailed, int inAppCreated) {
        transactionTemplate.executeWithoutResult(tx -> broadcastRepository.updateCompletion(
                broadcastId, status, totalRecipients,
                pushSent, pushFailed, smsSent, smsFailed, inAppCreated,
                OffsetDateTime.now()
        ));
    }

    private int processInApp(AdminBroadcastEntity broadcast, List<UUID> userIds) {
        List<UserNotificationEntity> notifications = userIds.stream().map(userId -> {
            UserNotificationEntity n = new UserNotificationEntity();
            n.setUserId(userId);
            n.setBroadcastId(broadcast.getId());
            n.setTitle(broadcast.getTitle());
            n.setBody(broadcast.getBody());
            return n;
        }).toList();
        userNotificationRepository.saveAll(notifications);

        List<AdminBroadcastDeliveryEntity> deliveries = userIds.stream()
                .map(userId -> delivery(broadcast.getId(), userId, "IN_APP", "SENT", null))
                .toList();
        deliveryRepository.saveAll(deliveries);

        return userIds.size();
    }

    private int[] processPush(AdminBroadcastEntity broadcast, List<UUID> userIds) {
        List<UserDeviceEntity> devices =
                deviceRepository.findByUserIdInAndIsActiveTrueAndProvider(userIds, pushSender.provider());
        Map<UUID, List<UserDeviceEntity>> devicesByUserId = devices.stream()
                .collect(Collectors.groupingBy(UserDeviceEntity::getUserId));
        if (devices.isEmpty()) {
            userIds.forEach(userId -> deliveryRepository.save(delivery(
                    broadcast.getId(), userId, "PUSH", "SKIPPED", "no active push device")));
            return new int[]{0, 0};
        }

        List<String> tokens = devices.stream().map(UserDeviceEntity::getToken).distinct().toList();
        Map<String, AdminPushSender.SendResult> results =
                pushSender.sendBatch(tokens, broadcast.getTitle(), broadcast.getBody(), broadcast.getId(),
                        broadcast.getRouteKey(), broadcast.getRouteTargetId());

        int sent = 0, failed = 0;
        for (UUID userId : userIds) {
            List<UserDeviceEntity> userDevices = devicesByUserId.getOrDefault(userId, List.of());
            if (userDevices.isEmpty()) {
                deliveryRepository.save(delivery(broadcast.getId(), userId, "PUSH", "SKIPPED", "no active push device"));
                continue;
            }

            boolean anySent = false;
            String error = null;
            for (UserDeviceEntity device : userDevices) {
                AdminPushSender.SendResult result = results.get(device.getToken());
                if (result != null && result.success()) {
                    anySent = true;
                    break;
                }
                if (error == null) {
                    error = result != null ? result.error() : "missing push response";
                }
            }

            deliveryRepository.save(delivery(broadcast.getId(), userId, "PUSH",
                    anySent ? "SENT" : "FAILED", anySent ? null : error));
            if (anySent) sent++; else failed++;
        }
        return new int[]{sent, failed};
    }

    private int[] processSms(AdminBroadcastEntity broadcast, List<UUID> userIds) {
        if (smsSender.isEmpty()) return new int[]{0, 0};

        List<UserEntity> users = userRepository.findAllById(userIds);
        Map<UUID, String> phoneByUserId = users.stream()
                .filter(u -> u.getPhoneNumber() != null && !u.getPhoneNumber().isBlank())
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getPhoneNumber));

        int sent = 0, failed = 0;
        for (UUID userId : userIds) {
            String phone = phoneByUserId.get(userId);
            if (phone == null) {
                deliveryRepository.save(delivery(broadcast.getId(), userId, "SMS", "SKIPPED", "no phone"));
                continue;
            }
            try {
                smsSender.get().send(phone, broadcast.getBody());
                deliveryRepository.save(delivery(broadcast.getId(), userId, "SMS", "SENT", null));
                sent++;
            } catch (Exception ex) {
                deliveryRepository.save(delivery(broadcast.getId(), userId, "SMS", "FAILED", truncate(ex.getMessage())));
                failed++;
            }
        }
        return new int[]{sent, failed};
    }

    private void resetStaleProcessing() {
        OffsetDateTime staleBefore = OffsetDateTime.now().minusMinutes(props.getStaleProcessingTimeoutMinutes());
        int reset = broadcastRepository.resetStaleProcessing(staleBefore);
        if (reset > 0) {
            log.warn("Reset {} stale PROCESSING broadcasts back to QUEUED", reset);
        }
    }

    private AdminBroadcastDeliveryEntity delivery(UUID broadcastId, UUID userId, String channel, String status, String error) {
        AdminBroadcastDeliveryEntity d = new AdminBroadcastDeliveryEntity();
        d.setBroadcastId(broadcastId);
        d.setUserId(userId);
        d.setChannel(channel);
        d.setStatus(status);
        d.setError(error);
        return d;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private static String truncate(String s) {
        return s != null && s.length() > 500 ? s.substring(0, 500) : s;
    }
}
