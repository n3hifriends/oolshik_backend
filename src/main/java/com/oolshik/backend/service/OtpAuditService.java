package com.oolshik.backend.service;

import com.oolshik.backend.entity.OtpAuditLogEntity;
import com.oolshik.backend.repo.OtpAuditLogRepository;
import com.oolshik.backend.util.MaskingUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpAuditService {

    private final OtpAuditLogRepository repository;

    public OtpAuditService(OtpAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String phone, String provider, String action, String status, String detail) {
        OtpAuditLogEntity entity = new OtpAuditLogEntity();
        entity.setMaskedPhone(MaskingUtils.maskPhone(phone));
        entity.setProvider(provider == null || provider.isBlank() ? "unknown" : provider);
        entity.setAction(action);
        entity.setStatus(status);
        entity.setDetail(detail);
        repository.save(entity);
    }
}
