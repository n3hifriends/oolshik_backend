package com.oolshik.notificationworker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "help_request_candidate")
public class HelpRequestCandidateEntity {

    @Id
    private UUID id;

    @Column(name = "help_request_id", nullable = false)
    private UUID helpRequestId;

    @Column(name = "helper_user_id", nullable = false)
    private UUID helperUserId;

    @Column(nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getHelpRequestId() {
        return helpRequestId;
    }

    public void setHelpRequestId(UUID helpRequestId) {
        this.helpRequestId = helpRequestId;
    }

    public UUID getHelperUserId() {
        return helperUserId;
    }

    public void setHelperUserId(UUID helperUserId) {
        this.helperUserId = helperUserId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
