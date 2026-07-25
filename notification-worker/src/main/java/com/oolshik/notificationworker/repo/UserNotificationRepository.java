package com.oolshik.notificationworker.repo;

import com.oolshik.notificationworker.entity.UserNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO user_notification (
            id, user_id, delivery_log_id, event_type, task_id, payment_request_id,
            route, title, body, created_at
        )
        VALUES (
            :id, :userId, :deliveryLogId, :eventType, :taskId, :paymentRequestId,
            :route, :title, :body, now()
        )
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("deliveryLogId") UUID deliveryLogId,
            @Param("eventType") String eventType,
            @Param("taskId") UUID taskId,
            @Param("paymentRequestId") UUID paymentRequestId,
            @Param("route") String route,
            @Param("title") String title,
            @Param("body") String body
    );
}
