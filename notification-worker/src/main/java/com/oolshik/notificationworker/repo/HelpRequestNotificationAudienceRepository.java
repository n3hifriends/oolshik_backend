package com.oolshik.notificationworker.repo;

import com.oolshik.notificationworker.entity.HelpRequestNotificationAudienceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HelpRequestNotificationAudienceRepository extends JpaRepository<HelpRequestNotificationAudienceEntity, UUID> {

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
