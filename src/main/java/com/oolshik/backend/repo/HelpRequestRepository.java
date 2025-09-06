package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.web.dto.HelpRequestDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
          h.voice_url                      AS voiceUrl,
          h.rating_value                   AS ratingValue,
          ha.avg_rating                    AS helperAvgRating,
          ST_Distance(
            h.location,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
          ) / 1000.0                       AS distanceKm
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
          distanceKm ASC,
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
}