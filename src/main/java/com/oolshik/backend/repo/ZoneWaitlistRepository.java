package com.oolshik.backend.repo;

import com.oolshik.backend.entity.ZoneWaitlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ZoneWaitlistRepository extends JpaRepository<ZoneWaitlistEntity, UUID> {

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO zone_waitlist (id, user_id, approx_lat, approx_lng, created_at, updated_at)
            VALUES (gen_random_uuid(), :userId, :lat, :lng, now(), now())
            ON CONFLICT (user_id) DO UPDATE SET
                approx_lat = excluded.approx_lat,
                approx_lng = excluded.approx_lng,
                updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId, @Param("lat") double lat, @Param("lng") double lng);
}
