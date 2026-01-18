package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelperLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface HelperLocationRepository extends JpaRepository<HelperLocationEntity, UUID> {

    @Query(value = """
        SELECT hl.helper_id
          FROM helper_location hl
          JOIN help_request h ON h.id = :requestId
         WHERE hl.last_seen_at >= :freshness
           AND ST_DWithin(hl.location, h.location, :newRadius)
           AND (:oldRadius IS NULL OR NOT ST_DWithin(hl.location, h.location, :oldRadius))
           AND hl.helper_id <> h.requester_id
           AND NOT EXISTS (
             SELECT 1
               FROM help_request_notify n
              WHERE n.request_id = h.id
                AND n.helper_id = hl.helper_id
                AND n.wave = :wave
           )
        """, nativeQuery = true)
    List<UUID> findNewlyEligibleHelpers(
            @Param("requestId") UUID requestId,
            @Param("newRadius") double newRadius,
            @Param("oldRadius") Double oldRadius,
            @Param("freshness") OffsetDateTime freshness,
            @Param("wave") int wave
    );
}
