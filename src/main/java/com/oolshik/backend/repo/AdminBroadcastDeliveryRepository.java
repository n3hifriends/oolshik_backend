package com.oolshik.backend.repo;

import com.oolshik.backend.entity.AdminBroadcastDeliveryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminBroadcastDeliveryRepository extends JpaRepository<AdminBroadcastDeliveryEntity, UUID> {
    Page<AdminBroadcastDeliveryEntity> findByBroadcastId(UUID broadcastId, Pageable pageable);
}
