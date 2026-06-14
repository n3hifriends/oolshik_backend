package com.oolshik.backend.repo;

import com.oolshik.backend.entity.AdminNotificationTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminNotificationTemplateRepository extends JpaRepository<AdminNotificationTemplateEntity, UUID> {
    boolean existsByName(String name);
}
