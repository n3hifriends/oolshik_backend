package com.oolshik.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "otp_audit_log")
public class OtpAuditLogEntity {

    @Id
    private UUID id;

    @Column(name = "masked_phone", nullable = false, length = 32)
    private String maskedPhone;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public void setMaskedPhone(String maskedPhone) {
        this.maskedPhone = maskedPhone;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
