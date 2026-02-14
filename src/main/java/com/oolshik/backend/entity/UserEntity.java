package com.oolshik.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import com.oolshik.backend.domain.Role;

@Entity
@Table(name = "app_user")
public class UserEntity {
    @Id
    private UUID id;

    @Column(name = "firebase_uid", unique = true)
    private String firebaseUid;

    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String displayName;

    @Column(nullable = false)
    private String roles; // comma-separated

    private String languages;

    @Column(name = "preferred_language", nullable = false, length = 16)
    private String preferredLanguage;

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
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setRoles(String roles) { this.roles = roles; }
    public void setLanguages(String languages) { this.languages = languages; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }


    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

}
