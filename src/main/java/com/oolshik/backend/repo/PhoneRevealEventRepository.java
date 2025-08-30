package com.oolshik.backend.repo;

import com.oolshik.backend.entity.PhoneRevealEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PhoneRevealEventRepository extends JpaRepository<PhoneRevealEventEntity, UUID> {
    long countByRequesterUserId(UUID requesterUserId);
}