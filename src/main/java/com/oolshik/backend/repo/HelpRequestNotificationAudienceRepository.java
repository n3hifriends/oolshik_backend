package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestNotificationAudienceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HelpRequestNotificationAudienceRepository extends JpaRepository<HelpRequestNotificationAudienceEntity, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO help_request_notification_audience (id, help_request_id, user_id, notified_for, radius_meters, created_at)
        VALUES (:id, :helpRequestId, :userId, :notifiedFor, :radiusMeters, now())
        ON CONFLICT (help_request_id, user_id, notified_for, radius_meters) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("helpRequestId") UUID helpRequestId,
            @Param("userId") UUID userId,
            @Param("notifiedFor") String notifiedFor,
            @Param("radiusMeters") Integer radiusMeters
    );

    @Query(value = """
        SELECT a.user_id
          FROM help_request_notification_audience a
         WHERE a.help_request_id = :helpRequestId
           AND a.notified_for = :notifiedFor
           AND (:radiusMeters IS NULL OR a.radius_meters = :radiusMeters)
        """, nativeQuery = true)
    List<UUID> findAudienceUserIds(
            @Param("helpRequestId") UUID helpRequestId,
            @Param("notifiedFor") String notifiedFor,
            @Param("radiusMeters") Integer radiusMeters
    );
}
