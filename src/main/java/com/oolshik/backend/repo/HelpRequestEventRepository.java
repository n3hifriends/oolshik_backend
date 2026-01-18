package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HelpRequestEventRepository extends JpaRepository<HelpRequestEventEntity, UUID> {
}
