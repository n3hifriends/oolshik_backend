package com.oolshik.backend.service;

import com.oolshik.backend.admin.AdminNotificationDtos.UnreadCountResponse;
import com.oolshik.backend.admin.AdminNotificationDtos.UserNotificationResponse;
import com.oolshik.backend.entity.UserNotificationEntity;
import com.oolshik.backend.repo.UserNotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserNotificationService {

    private final UserNotificationRepository repository;

    public UserNotificationService(UserNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<UserNotificationResponse> getInbox(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID userId) {
        return new UnreadCountResponse(repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public UserNotificationResponse markRead(UUID userId, UUID notificationId) {
        UserNotificationEntity entity = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));
        if (entity.getReadAt() == null) {
            entity.setReadAt(OffsetDateTime.now());
            entity = repository.save(entity);
        }
        return toResponse(entity);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, OffsetDateTime.now());
    }

    private UserNotificationResponse toResponse(UserNotificationEntity e) {
        return new UserNotificationResponse(
                e.getId(), e.getTitle(), e.getBody(), e.getReadAt() != null, e.getCreatedAt(),
                e.getBroadcastId(), e.getEventType(), e.getTaskId(), e.getPaymentRequestId(), e.getRoute());
    }
}
