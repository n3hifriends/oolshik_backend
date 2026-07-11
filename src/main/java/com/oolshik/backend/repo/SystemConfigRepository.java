package com.oolshik.backend.repo;

import com.oolshik.backend.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, String> {
}
