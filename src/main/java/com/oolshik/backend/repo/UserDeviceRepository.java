package com.oolshik.backend.repo;

import com.oolshik.backend.entity.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {
    Optional<UserDeviceEntity> findByTokenHash(String tokenHash);
    List<UserDeviceEntity> findByUserIdAndIsActiveTrue(UUID userId);
    List<UserDeviceEntity> findByUserIdInAndIsActiveTrue(Collection<UUID> userIds);
    List<UserDeviceEntity> findByUserIdInAndIsActiveTrueAndProvider(Collection<UUID> userIds, String provider);
}
