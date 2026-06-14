// src/main/java/com/oolshik/backend/repo/ReportEventRepository.java
package com.oolshik.backend.repo;

import com.oolshik.backend.entity.ReportEventEntity;
import com.oolshik.backend.domain.ReportReason;
import com.oolshik.backend.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.UUID;

public interface ReportEventRepository extends JpaRepository<ReportEventEntity, UUID>, JpaSpecificationExecutor<ReportEventEntity> {
    long countByStatusIn(Collection<ReportStatus> statuses);
    long countByReason(ReportReason reason);
}
