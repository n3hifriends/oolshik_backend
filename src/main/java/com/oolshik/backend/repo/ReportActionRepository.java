package com.oolshik.backend.repo;

import com.oolshik.backend.entity.ReportActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportActionRepository extends JpaRepository<ReportActionEntity, UUID> {
    List<ReportActionEntity> findTop50ByReportIdOrderByCreatedAtDesc(UUID reportId);
}
