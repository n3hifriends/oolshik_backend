package com.oolshik.backend.repo;

import com.oolshik.backend.entity.OtpAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OtpAuditLogRepository extends JpaRepository<OtpAuditLogEntity, UUID> {
    @Query(value = """
            select o from OtpAuditLogEntity o
            where (:status is null or o.status = :status)
            order by o.createdAt desc
            """,
            countQuery = """
            select count(o) from OtpAuditLogEntity o
            where (:status is null or o.status = :status)
            """)
    Page<OtpAuditLogEntity> findForAdmin(@Param("status") String status, Pageable pageable);

    List<OtpAuditLogEntity> findTop20ByPhoneHashOrderByCreatedAtDesc(String phoneHash);
}
