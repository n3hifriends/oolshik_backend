package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface HelpRequestRepository extends JpaRepository<HelpRequestEntity, UUID> {

  @Query(
          value = """
        WITH helper_avg AS (
          SELECT helper_id, AVG(rating_value)::numeric(3,2) AS avg_rating
          FROM help_request
          WHERE rating_value IS NOT NULL
          GROUP BY helper_id
        )
        SELECT
          h.id,
          h.title,
          h.description,
          ST_Y(h.location::geometry)       AS latitude,
          ST_X(h.location::geometry)       AS longitude,
          h.radius_meters                  AS radiusMeters,
          h.status,
          h.requester_id                   AS requesterId,
          u.display_name                   AS createdByName,
          u.phone_number                   AS createdByPhoneNumber,
          h.helper_id                      AS helperId,
          h.created_at                     AS createdAt,
          h.updated_at                     AS UpdatedAt,
          h.helper_accepted_at             AS helperAcceptedAt,
          h.assignment_expires_at          AS assignmentExpiresAt,
          h.cancelled_at                   AS cancelledAt,
          h.reassigned_count               AS reassignedCount,
          h.released_count                 AS releasedCount,
          h.radius_stage                   AS radiusStage,
          h.next_escalation_at             AS nextEscalationAt,
          h.voice_url                      AS voiceUrl,
          h.rating_value                   AS ratingValue,
          ha.avg_rating                    AS helperAvgRating,
          ST_Distance(
            h.location,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
          )                                AS distanceMtr
        FROM help_request h
        JOIN app_user u ON u.id = h.requester_id
        LEFT JOIN helper_avg ha ON ha.helper_id = h.helper_id
        WHERE
          (COALESCE(:statusesCsv, '') = '' OR h.status::text = ANY(string_to_array(:statusesCsv, ',')))
          AND ST_DWithin(
            h.location,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            :radiusMeters
          )
        ORDER BY
          distanceMtr ASC,
          h.created_at DESC
        """,
          countQuery = """
        SELECT COUNT(*)
        FROM help_request h
        WHERE
          (COALESCE(:statusesCsv, '') = '' OR h.status::text = ANY(string_to_array(:statusesCsv, ',')))
          AND ST_DWithin(
            h.location,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            :radiusMeters
          )
        """,
          nativeQuery = true
  )
  Page<HelpRequestRow> findNearbyPaged(
          @Param("lat") double lat,
          @Param("lng") double lng,
          @Param("radiusMeters") int radiusMeters,
          @Param("statusesCsv") String statusesCsv,
          Pageable pageable
  );


  @Query(
          value = """
      SELECT
        h.id,
        h.title,
        h.description,
        ST_Y(h.location::geometry)       AS latitude,
        ST_X(h.location::geometry)       AS longitude,
        h.radius_meters                  AS radiusMeters,
        h.status,
        h.requester_id                   AS requesterId,
        u.display_name                   AS createdByName,
        u.phone_number                   AS createdByPhoneNumber,
        h.helper_id                      AS helperId,
        h.created_at                     AS createdAt,
        h.updated_at                     AS updatedAt,
        h.helper_accepted_at             AS helperAcceptedAt,
        h.assignment_expires_at          AS assignmentExpiresAt,
        h.cancelled_at                   AS cancelledAt,
        h.reassigned_count               AS reassignedCount,
        h.released_count                 AS releasedCount,
        h.radius_stage                   AS radiusStage,
        h.next_escalation_at             AS nextEscalationAt,
        h.voice_url                      AS voiceUrl,
        h.rating_value                   AS ratingValue,
        COALESCE((
          SELECT AVG(hr2.rating_value)::numeric(3,2)
          FROM help_request hr2
          WHERE hr2.helper_id = h.helper_id
            AND hr2.rating_value IS NOT NULL
        ), 0.00)                         AS helperAvgRating
      FROM help_request h
      JOIN app_user u ON u.id = h.requester_id
      WHERE h.id = :taskId
    """,
          nativeQuery = true
  )
  HelpRequestRow findTaskByTaskId(@Param("taskId") java.util.UUID taskId);

  @Modifying
  @Query("""
      update HelpRequestEntity h
         set h.status = :newStatus,
             h.helperId = :helperId,
             h.helperAcceptLocation = :acceptLocation,
             h.helperAcceptedAt = :acceptedAt,
             h.assignmentExpiresAt = :expiresAt,
             h.nextEscalationAt = null,
             h.lastStateChangeAt = :acceptedAt,
             h.lastStateChangeReason = :stateReason,
             h.updatedAt = :acceptedAt
       where h.id = :id
         and h.status = :expectedStatus
      """)
  int updateAccept(
          @Param("id") UUID id,
          @Param("helperId") UUID helperId,
          @Param("acceptLocation") org.locationtech.jts.geom.Point acceptLocation,
          @Param("acceptedAt") OffsetDateTime acceptedAt,
          @Param("expiresAt") OffsetDateTime expiresAt,
          @Param("expectedStatus") com.oolshik.backend.domain.HelpRequestStatus expectedStatus,
          @Param("newStatus") com.oolshik.backend.domain.HelpRequestStatus newStatus,
          @Param("stateReason") String stateReason
  );

  @Modifying
  @Query("""
      update HelpRequestEntity h
         set h.status = :newStatus,
             h.cancelledAt = :now,
             h.cancelledBy = :actorId,
             h.cancelReasonCode = :reasonCode,
             h.cancelReasonText = :reasonText,
             h.nextEscalationAt = null,
             h.lastStateChangeAt = :now,
             h.lastStateChangeReason = :stateReason,
             h.updatedAt = :now
       where h.id = :id
         and h.requesterId = :requesterId
         and h.status in :allowedStatuses
      """)
  int updateCancel(
          @Param("id") UUID id,
          @Param("requesterId") UUID requesterId,
          @Param("actorId") UUID actorId,
          @Param("now") OffsetDateTime now,
          @Param("reasonCode") String reasonCode,
          @Param("reasonText") String reasonText,
          @Param("stateReason") String stateReason,
          @Param("allowedStatuses") List<com.oolshik.backend.domain.HelpRequestStatus> allowedStatuses,
          @Param("newStatus") com.oolshik.backend.domain.HelpRequestStatus newStatus
  );

  @Modifying
  @Query("""
      update HelpRequestEntity h
         set h.status = :newStatus,
             h.helperId = null,
             h.helperAcceptLocation = null,
             h.helperAcceptedAt = null,
             h.assignmentExpiresAt = null,
             h.releasedAt = :now,
             h.releasedCount = coalesce(h.releasedCount, 0) + 1,
             h.nextEscalationAt = :nextEscalationAt,
             h.lastStateChangeAt = :now,
             h.lastStateChangeReason = :stateReason,
             h.updatedAt = :now
       where h.id = :id
         and h.helperId = :helperId
         and h.status in :allowedStatuses
      """)
  int updateRelease(
          @Param("id") UUID id,
          @Param("helperId") UUID helperId,
          @Param("now") OffsetDateTime now,
          @Param("nextEscalationAt") OffsetDateTime nextEscalationAt,
          @Param("stateReason") String stateReason,
          @Param("allowedStatuses") List<com.oolshik.backend.domain.HelpRequestStatus> allowedStatuses,
          @Param("newStatus") com.oolshik.backend.domain.HelpRequestStatus newStatus
  );

  @Modifying
  @Query("""
      update HelpRequestEntity h
         set h.status = :newStatus,
             h.helperId = null,
             h.helperAcceptLocation = null,
             h.helperAcceptedAt = null,
             h.assignmentExpiresAt = null,
             h.reassignedCount = coalesce(h.reassignedCount, 0) + 1,
             h.nextEscalationAt = :nextEscalationAt,
             h.lastStateChangeAt = :now,
             h.lastStateChangeReason = :stateReason,
             h.updatedAt = :now
       where h.id = :id
         and h.requesterId = :requesterId
         and h.status = :expectedStatus
         and h.helperAcceptedAt <= :minAcceptedAt
         and coalesce(h.reassignedCount, 0) < :maxReassign
      """)
  int updateReassign(
          @Param("id") UUID id,
          @Param("requesterId") UUID requesterId,
          @Param("now") OffsetDateTime now,
          @Param("minAcceptedAt") OffsetDateTime minAcceptedAt,
          @Param("maxReassign") int maxReassign,
          @Param("nextEscalationAt") OffsetDateTime nextEscalationAt,
          @Param("expectedStatus") com.oolshik.backend.domain.HelpRequestStatus expectedStatus,
          @Param("newStatus") com.oolshik.backend.domain.HelpRequestStatus newStatus,
          @Param("stateReason") String stateReason
  );

  @Modifying
  @Query("""
      update HelpRequestEntity h
         set h.status = :newStatus,
             h.helperId = null,
             h.helperAcceptLocation = null,
             h.helperAcceptedAt = null,
             h.assignmentExpiresAt = null,
             h.reassignedCount = coalesce(h.reassignedCount, 0) + 1,
             h.nextEscalationAt = :nextEscalationAt,
             h.lastStateChangeAt = :now,
             h.lastStateChangeReason = :stateReason,
             h.updatedAt = :now
       where h.id = :id
         and h.status = :expectedStatus
         and h.assignmentExpiresAt is not null
         and h.assignmentExpiresAt <= :now
      """)
  int updateAutoRelease(
          @Param("id") UUID id,
          @Param("now") OffsetDateTime now,
          @Param("nextEscalationAt") OffsetDateTime nextEscalationAt,
          @Param("expectedStatus") com.oolshik.backend.domain.HelpRequestStatus expectedStatus,
          @Param("newStatus") com.oolshik.backend.domain.HelpRequestStatus newStatus,
          @Param("stateReason") String stateReason
  );

  @Modifying
  @Query("""
      update HelpRequestEntity h
         set h.radiusStage = :newStage,
             h.radiusMeters = :newRadius,
             h.nextEscalationAt = :nextEscalationAt,
             h.lastStateChangeAt = :now,
             h.lastStateChangeReason = :stateReason,
             h.updatedAt = :now
       where h.id = :id
         and h.status = :expectedStatus
         and h.radiusStage = :expectedStage
         and h.nextEscalationAt is not null
         and h.nextEscalationAt <= :now
      """)
  int updateRadiusEscalation(
          @Param("id") UUID id,
          @Param("now") OffsetDateTime now,
          @Param("expectedStage") int expectedStage,
          @Param("newStage") int newStage,
          @Param("newRadius") int newRadius,
          @Param("nextEscalationAt") OffsetDateTime nextEscalationAt,
          @Param("expectedStatus") com.oolshik.backend.domain.HelpRequestStatus expectedStatus,
          @Param("stateReason") String stateReason
  );

  @Modifying
  @Query("""
      update HelpRequestEntity h
         set h.nextEscalationAt = null,
             h.lastStateChangeAt = :now,
             h.lastStateChangeReason = :stateReason,
             h.updatedAt = :now
       where h.id = :id
         and h.status = :expectedStatus
         and h.nextEscalationAt is not null
         and h.nextEscalationAt <= :now
      """)
  int stopRadiusEscalation(
          @Param("id") UUID id,
          @Param("now") OffsetDateTime now,
          @Param("expectedStatus") com.oolshik.backend.domain.HelpRequestStatus expectedStatus,
          @Param("stateReason") String stateReason
  );

  @Query("""
      select h.id
        from HelpRequestEntity h
       where h.status = :status
         and h.nextEscalationAt is not null
         and h.nextEscalationAt <= :now
      """)
  List<UUID> findRadiusEscalationCandidates(
          @Param("status") com.oolshik.backend.domain.HelpRequestStatus status,
          @Param("now") OffsetDateTime now,
          Pageable pageable
  );

  @Query("""
      select h.id
        from HelpRequestEntity h
       where h.status = :status
         and h.assignmentExpiresAt is not null
         and h.assignmentExpiresAt <= :now
      """)
  List<UUID> findExpiredAssignments(
          @Param("status") com.oolshik.backend.domain.HelpRequestStatus status,
          @Param("now") OffsetDateTime now,
          Pageable pageable
  );
}
