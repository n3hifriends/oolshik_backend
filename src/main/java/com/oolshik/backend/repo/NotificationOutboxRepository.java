package com.oolshik.backend.repo;

import com.oolshik.backend.entity.NotificationOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntity, UUID> {

    @Query(value = """
        SELECT *
          FROM notification_outbox
         WHERE status IN (:statuses)
           AND next_attempt_at <= :now
         ORDER BY created_at
         FOR UPDATE SKIP LOCKED
         LIMIT :limit
        """, nativeQuery = true)
    List<NotificationOutboxEntity> lockNextBatch(
            @Param("statuses") List<String> statuses,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );

    @Modifying
    @Query("""
        update NotificationOutboxEntity o
           set o.status = :status,
               o.attemptCount = :attemptCount,
               o.nextAttemptAt = :nextAttemptAt,
               o.lastError = :lastError,
               o.updatedAt = :updatedAt
         where o.id = :id
        """)
    int updateStatus(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
