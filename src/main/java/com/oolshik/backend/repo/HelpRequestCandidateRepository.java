package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HelpRequestCandidateRepository extends JpaRepository<HelpRequestCandidateEntity, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO help_request_candidate (id, help_request_id, helper_user_id, state, created_at, updated_at)
        VALUES (:id, :helpRequestId, :helperUserId, :state, now(), now())
        ON CONFLICT (help_request_id, helper_user_id) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("helpRequestId") UUID helpRequestId,
            @Param("helperUserId") UUID helperUserId,
            @Param("state") String state
    );

    @Query(value = """
        SELECT c.helper_user_id
          FROM help_request_candidate c
         WHERE c.help_request_id = :helpRequestId
           AND c.state IN (:states)
        """, nativeQuery = true)
    List<UUID> findHelperIdsByRequestIdAndStates(
            @Param("helpRequestId") UUID helpRequestId,
            @Param("states") List<String> states
    );

    @Modifying
    @Query(value = """
        UPDATE help_request_candidate
           SET state = :state,
               updated_at = now()
         WHERE help_request_id = :helpRequestId
           AND helper_user_id IN (:helperIds)
        """, nativeQuery = true)
    int updateStates(
            @Param("helpRequestId") UUID helpRequestId,
            @Param("helperIds") List<UUID> helperIds,
            @Param("state") String state
    );
}
