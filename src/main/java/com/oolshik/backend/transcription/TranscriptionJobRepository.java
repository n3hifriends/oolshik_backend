package com.oolshik.backend.transcription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJobEntity, UUID> {
    Optional<TranscriptionJobEntity> findByTaskId(UUID taskId);

    List<TranscriptionJobEntity> findTop50ByStatusOrderByUpdatedAtAsc(TranscriptionStatus status);

    long countByStatus(TranscriptionStatus status);

    @Query(value = """
            select t from TranscriptionJobEntity t
            where (:status is null or t.status = :status)
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t) from TranscriptionJobEntity t
            where (:status is null or t.status = :status)
            """)
    Page<TranscriptionJobEntity> findForAdmin(@Param("status") TranscriptionStatus status, Pageable pageable);
}
