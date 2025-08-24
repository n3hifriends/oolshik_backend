package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface HelpRequestRepository extends JpaRepository<HelpRequestEntity, UUID> {

    @Query(
    value = """
      SELECT h.*
      FROM help_request h
      WHERE
        ( COALESCE(:statusesCsv, '') = '' OR h.status::text = ANY(string_to_array(:statusesCsv, ',')) )
        AND (
          6371.0 * acos(
            LEAST(GREATEST(
              cos(radians(:lat)) * cos(radians(h.latitude)) *
              cos(radians(h.longitude) - radians(:lng)) +
              sin(radians(:lat)) * sin(radians(h.latitude))
            , -1), 1)
          )
        ) <= (:radiusMeters / 1000.0)
      ORDER BY
        ( 6371.0 * acos(
          LEAST(GREATEST(
            cos(radians(:lat)) * cos(radians(h.latitude)) *
            cos(radians(h.longitude) - radians(:lng)) +
            sin(radians(:lat)) * sin(radians(h.latitude))
          , -1), 1)
        ) ) ASC,
        h.created_at DESC
      """,
    countQuery = """
      SELECT COUNT(*)
      FROM help_request h
      WHERE
        ( COALESCE(:statusesCsv, '') = '' OR h.status::text = ANY(string_to_array(:statusesCsv, ',')) )
        AND (
          6371.0 * acos(
            LEAST(GREATEST(
              cos(radians(:lat)) * cos(radians(h.latitude)) *
              cos(radians(h.longitude) - radians(:lng)) +
              sin(radians(:lat)) * sin(radians(h.latitude))
            , -1), 1)
          )
        ) <= (:radiusMeters / 1000.0)
      """,
    nativeQuery = true
  )
  Page<HelpRequestEntity> findNearbyPaged(
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusMeters") int radiusMeters,
      @Param("statusesCsv") String statusesCsv, // pass comma-joined statuses or "" or null
      Pageable pageable
  );
}