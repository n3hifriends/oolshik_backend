package com.oolshik.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import com.oolshik.backend.domain.OnboardingPhase;
import com.oolshik.backend.domain.Role;

@Entity
@Table(name = "app_user")
public class UserEntity {
    @Id
    private UUID id;

    @Column(name = "firebase_uid", unique = true)
    private String firebaseUid;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash")
    private String passwordHash;

    private String displayName;

    @Column(nullable = false)
    private String roles; // comma-separated

    private String languages;

    @Column(name = "preferred_language", nullable = false, length = 16)
    private String preferredLanguage;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;

    @Column(name = "blocked_reason", length = 512)
    private String blockReason;

    @Column(name = "blocked_by")
    private UUID blockedBy;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_phase", length = 32)
    private OnboardingPhase onboardingPhase;

    @Column(name = "zone_confirmed", nullable = false)
    private boolean zoneConfirmed;

    @Column(name = "confirmed_zone_id")
    private UUID confirmedZoneId;

    @Column(name = "zone_confirmed_at")
    private OffsetDateTime zoneConfirmedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (roles == null || roles.isBlank()) roles = "NETA";
        if (preferredLanguage == null || preferredLanguage.isBlank()) preferredLanguage = "en-IN";
        if (onboardingPhase == null) onboardingPhase = OnboardingPhase.FRESH;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public Set<Role> getRoleSet() {
        Set<Role> set = new HashSet<>();
        if (roles == null || roles.isBlank()) return set;
        for (String r : roles.split(",")) {
            try { set.add(Role.valueOf(r.trim())); } catch (Exception ignored) {}
        }
        return set;
    }

    public void setRoleSet(Set<Role> set) {
        this.roles = String.join(",", set.stream().map(Enum::name).toList());
    }

    // getters/setters
    public UUID getId() { return id; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getRoles() { return roles; }
    public String getLanguages() { return languages; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setEmail(String email) { this.email = email; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setRoles(String roles) { this.roles = roles; }
    public void setLanguages(String languages) { this.languages = languages; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public boolean isBlocked() { return blocked; }
    public OffsetDateTime getBlockedAt() { return blockedAt; }
    public String getBlockReason() { return blockReason; }
    public UUID getBlockedBy() { return blockedBy; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public void setBlockedAt(OffsetDateTime blockedAt) { this.blockedAt = blockedAt; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    public void setBlockedBy(UUID blockedBy) { this.blockedBy = blockedBy; }
    public boolean isDeleted() { return deleted; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }


    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public OnboardingPhase getOnboardingPhase() {
        return onboardingPhase;
    }

    public void setOnboardingPhase(OnboardingPhase onboardingPhase) {
        this.onboardingPhase = onboardingPhase;
    }

    public boolean isZoneConfirmed() { return zoneConfirmed; }
    public void setZoneConfirmed(boolean zoneConfirmed) { this.zoneConfirmed = zoneConfirmed; }
    public UUID getConfirmedZoneId() { return confirmedZoneId; }
    public void setConfirmedZoneId(UUID confirmedZoneId) { this.confirmedZoneId = confirmedZoneId; }
    public OffsetDateTime getZoneConfirmedAt() { return zoneConfirmedAt; }
    public void setZoneConfirmedAt(OffsetDateTime zoneConfirmedAt) { this.zoneConfirmedAt = zoneConfirmedAt; }
}
