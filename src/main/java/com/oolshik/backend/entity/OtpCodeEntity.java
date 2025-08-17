package com.oolshik.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "otp_code")
public class OtpCodeEntity {
    @Id
    private UUID id;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private String purpose; // LOGIN

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "resend_count", nullable = false)
    private int resendCount;

    @Column(name = "last_sent_at", nullable = false)
    private OffsetDateTime lastSentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (lastSentAt == null) lastSentAt = createdAt;
        if (attemptCount < 0) attemptCount = 0;
        if (resendCount < 0) resendCount = 0;
    }

    // getters/setters
    public UUID getId() { return id; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getCodeHash() { return codeHash; }
    public String getPurpose() { return purpose; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public int getAttemptCount() { return attemptCount; }
    public int getResendCount() { return resendCount; }
    public OffsetDateTime getLastSentAt() { return lastSentAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setId(UUID id) { this.id = id; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setConsumedAt(OffsetDateTime consumedAt) { this.consumedAt = consumedAt; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setResendCount(int resendCount) { this.resendCount = resendCount; }
    public void setLastSentAt(OffsetDateTime lastSentAt) { this.lastSentAt = lastSentAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
