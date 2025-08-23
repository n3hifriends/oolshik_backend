package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

// HelpRequestRepository.java
public interface HelpRequestRepository extends JpaRepository<HelpRequestEntity, UUID> {

  @Query(
      value = """
        SELECT h.*
        FROM help_request h
        WHERE h.status = 'OPEN'
          AND (
            6371 * acos(
              LEAST(GREATEST(
                cos(radians(:lat)) * cos(radians(h.latitude)) *
                cos(radians(h.longitude) - radians(:lng)) +
                sin(radians(:lat)) * sin(radians(h.latitude))
              , -1), 1)
            )
          ) <= :radiusKm
        ORDER BY
          (6371 * acos(
            LEAST(GREATEST(
              cos(radians(:lat)) * cos(radians(h.latitude)) *
              cos(radians(h.longitude) - radians(:lng)) +
              sin(radians(:lat)) * sin(radians(h.latitude))
            , -1), 1)
          )) ASC
        """,
      nativeQuery = true)
  List<HelpRequestEntity> findNearby(
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusKm") double radiusKm);
}