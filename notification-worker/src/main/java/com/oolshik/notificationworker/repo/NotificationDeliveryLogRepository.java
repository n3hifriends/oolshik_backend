package com.oolshik.notificationworker.repo;

import com.oolshik.notificationworker.entity.NotificationDeliveryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLogEntity, UUID> {

    Optional<NotificationDeliveryLogEntity> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("""
        update NotificationDeliveryLogEntity l
           set l.status = :status,
               l.lastError = :lastError,
               l.updatedAt = :updatedAt
         where l.id = :id
        """)
    int updateStatus(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("lastError") String lastError,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
