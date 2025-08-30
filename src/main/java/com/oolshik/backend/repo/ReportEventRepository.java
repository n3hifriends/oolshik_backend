// src/main/java/com/oolshik/backend/repo/ReportEventRepository.java
package com.oolshik.backend.repo;

import com.oolshik.backend.entity.ReportEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportEventRepository extends JpaRepository<ReportEventEntity, UUID> {
}