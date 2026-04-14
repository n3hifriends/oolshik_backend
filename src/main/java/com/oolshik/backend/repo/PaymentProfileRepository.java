package com.oolshik.backend.repo;

import com.oolshik.backend.entity.PaymentProfileEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentProfileRepository extends JpaRepository<PaymentProfileEntity, UUID> {

    Optional<PaymentProfileEntity> findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentProfileEntity p
            where p.userId = :userId and p.active = true
            order by p.updatedAt desc
            """)
    Optional<PaymentProfileEntity> findActiveForUpdate(@Param("userId") UUID userId);
}
