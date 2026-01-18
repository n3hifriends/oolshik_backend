package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestNotifyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface HelpRequestNotifyRepository extends JpaRepository<HelpRequestNotifyEntity, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO help_request_notify (id, request_id, helper_id, wave, notified_at)
        VALUES (:id, :requestId, :helperId, :wave, now())
        ON CONFLICT (request_id, helper_id, wave) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("requestId") UUID requestId,
            @Param("helperId") UUID helperId,
            @Param("wave") int wave
    );
}
