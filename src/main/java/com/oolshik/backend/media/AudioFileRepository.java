package com.oolshik.backend.media;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AudioFileRepository extends JpaRepository<AudioFile, UUID> {
    List<AudioFile> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);
}