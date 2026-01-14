package com.oolshik.backend.transcription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJobEntity, UUID> {
    Optional<TranscriptionJobEntity> findByTaskId(UUID taskId);

    List<TranscriptionJobEntity> findTop50ByStatusOrderByUpdatedAtAsc(TranscriptionStatus status);
}
