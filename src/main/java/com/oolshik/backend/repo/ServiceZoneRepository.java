package com.oolshik.backend.repo;

import com.oolshik.backend.entity.ServiceZoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceZoneRepository extends JpaRepository<ServiceZoneEntity, UUID> {
    List<ServiceZoneEntity> findByActiveTrue();
}
