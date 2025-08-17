package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface HelpRequestRepository extends JpaRepository<HelpRequestEntity, UUID> {

    // Approx distance (meters) using equirectangular approximation
    @Query(value = "SELECT * FROM help_request hr " +
            "WHERE hr.status = :status " +
            "AND (111320 * sqrt(power(hr.latitude - :lat, 2) + power((hr.longitude - :lon) * cos(radians(:lat)), 2))) <= :radiusMeters",
            nativeQuery = true)
    List<HelpRequestEntity> findNearbyOpen(
        @Param("status") String status,
        @Param("lat") double lat,
        @Param("lon") double lon,
        @Param("radiusMeters") int radiusMeters
    );
}
