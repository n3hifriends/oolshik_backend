package com.oolshik.backend.repo;

import com.oolshik.backend.entity.OtpCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCodeEntity, UUID> {

    @Query("SELECT o FROM OtpCodeEntity o WHERE o.phoneNumber = ?1 AND o.purpose = ?2 AND o.expiresAt > ?3 AND o.consumedAt IS NULL ORDER BY o.createdAt DESC")
    List<OtpCodeEntity> findActive(String phone, String purpose, OffsetDateTime now);

    Optional<OtpCodeEntity> findFirstByPhoneNumberOrderByCreatedAtDesc(String phone);
}
