package com.oolshik.backend.repo;

import com.oolshik.backend.entity.NotificationOutboxEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntity, UUID> {
    long countByStatusIn(Collection<String> statuses);

    @Query("""
        select count(n) from NotificationOutboxEntity n
        where n.status = 'FAILED'
           or (n.status = 'DEAD' and n.acknowledgedAt is null)
        """)
    long countUnresolvedFailures();

    @Query(value = """
        select n from NotificationOutboxEntity n
        where (:status is null or n.status = :status)
        order by n.createdAt desc
        """,
        countQuery = """
        select count(n) from NotificationOutboxEntity n
        where (:status is null or n.status = :status)
        """)
    Page<NotificationOutboxEntity> findForAdmin(@Param("status") String status, Pageable pageable);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update NotificationOutboxEntity o
           set o.acknowledgedAt = :acknowledgedAt,
               o.acknowledgedBy = :acknowledgedBy,
               o.resolutionNote = :resolutionNote,
               o.updatedAt = :acknowledgedAt
         where o.id = :id
           and o.status = 'DEAD'
           and o.acknowledgedAt is null
        """)
    int acknowledgeDead(
            @Param("id") UUID id,
            @Param("acknowledgedBy") UUID acknowledgedBy,
            @Param("resolutionNote") String resolutionNote,
            @Param("acknowledgedAt") OffsetDateTime acknowledgedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update NotificationOutboxEntity o
           set o.status = 'PENDING',
               o.attemptCount = 0,
               o.nextAttemptAt = :now,
               o.lastError = null,
               o.acknowledgedAt = null,
               o.acknowledgedBy = null,
               o.resolutionNote = null,
               o.updatedAt = :now
         where o.id = :id
           and (o.status = 'DEAD' or (o.status = 'FAILED' and o.nextAttemptAt <= :now))
        """)
    int requeueFailure(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
