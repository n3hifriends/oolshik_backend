package com.oolshik.notificationworker.repo;

import com.oolshik.notificationworker.entity.UserNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, UUID> {
}
