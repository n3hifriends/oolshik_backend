package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminNotificationDtos.CreateTemplateRequest;
import com.oolshik.backend.admin.AdminNotificationDtos.TemplateResponse;
import com.oolshik.backend.entity.AdminNotificationTemplateEntity;
import com.oolshik.backend.repo.AdminNotificationTemplateRepository;
import com.oolshik.backend.web.error.ConflictOperationException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminNotificationTemplateService {

    private final AdminNotificationTemplateRepository repository;

    public AdminNotificationTemplateService(AdminNotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TemplateResponse create(CreateTemplateRequest request, UUID createdBy) {
        if (repository.existsByName(request.name())) {
            throw new ConflictOperationException("Template name already exists: " + request.name());
        }
        AdminNotificationTemplateEntity entity = new AdminNotificationTemplateEntity();
        entity.setName(request.name());
        entity.setTitle(request.title());
        entity.setBody(request.body());
        entity.setCreatedBy(createdBy);
        entity = repository.save(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Template not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public AdminNotificationTemplateEntity saveAsTemplate(String name, String title, String body, UUID createdBy) {
        if (repository.existsByName(name)) {
            throw new ConflictOperationException("Template name already exists: " + name);
        }
        AdminNotificationTemplateEntity entity = new AdminNotificationTemplateEntity();
        entity.setName(name);
        entity.setTitle(title);
        entity.setBody(body);
        entity.setCreatedBy(createdBy);
        return repository.save(entity);
    }

    private TemplateResponse toResponse(AdminNotificationTemplateEntity e) {
        return new TemplateResponse(e.getId(), e.getName(), e.getTitle(), e.getBody(), e.getCreatedBy(), e.getCreatedAt());
    }
}
