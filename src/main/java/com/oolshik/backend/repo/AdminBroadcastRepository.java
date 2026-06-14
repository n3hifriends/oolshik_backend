package com.oolshik.backend.repo;

import com.oolshik.backend.entity.AdminBroadcastEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminBroadcastRepository extends JpaRepository<AdminBroadcastEntity, UUID> {

    Page<AdminBroadcastEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = """
        SELECT id FROM admin_broadcast
         WHERE status = 'QUEUED'
         ORDER BY created_at
         LIMIT 1
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<UUID> lockNextQueued();

    @Modifying
    @Query("""
        UPDATE AdminBroadcastEntity b
           SET b.status = 'PROCESSING',
               b.processingStartedAt = :now
         WHERE b.id = :id
           AND b.status = 'QUEUED'
        """)
    int claimById(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
        UPDATE AdminBroadcastEntity b
           SET b.status = 'QUEUED',
               b.processingStartedAt = null
         WHERE b.status = 'PROCESSING'
           AND b.processingStartedAt < :staleBefore
        """)
    int resetStaleProcessing(@Param("staleBefore") OffsetDateTime staleBefore);

    @Modifying
    @Query("""
        UPDATE AdminBroadcastEntity b
           SET b.status = :status,
               b.totalRecipients = :totalRecipients,
               b.pushSent = :pushSent,
               b.pushFailed = :pushFailed,
               b.smsSent = :smsSent,
               b.smsFailed = :smsFailed,
               b.inAppCreated = :inAppCreated,
               b.completedAt = :completedAt
         WHERE b.id = :id
        """)
    int updateCompletion(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("totalRecipients") int totalRecipients,
            @Param("pushSent") int pushSent,
            @Param("pushFailed") int pushFailed,
            @Param("smsSent") int smsSent,
            @Param("smsFailed") int smsFailed,
            @Param("inAppCreated") int inAppCreated,
            @Param("completedAt") OffsetDateTime completedAt
    );
}
