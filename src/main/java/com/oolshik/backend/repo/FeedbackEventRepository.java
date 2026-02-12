package com.oolshik.backend.repo;

import com.oolshik.backend.entity.FeedbackEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackEventRepository extends JpaRepository<FeedbackEventEntity, UUID> {
    Optional<FeedbackEventEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    long countByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime since);

    @Modifying
    @Query("delete from FeedbackEventEntity f where f.retentionUntil < :cutoff")
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}
