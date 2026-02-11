package com.oolshik.backend.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public class NotificationEventContext {

    private UUID actorUserId;
    private String previousStatus;
    private String newStatus;
    private AssignmentChange assignmentChange = AssignmentChange.NONE;
    private UUID previousHelperId;
    private UUID newHelperId;
    private Integer previousRadiusMeters;
    private Integer newRadiusMeters;
    private OffsetDateTime occurredAt;

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public AssignmentChange getAssignmentChange() {
        return assignmentChange;
    }

    public void setAssignmentChange(AssignmentChange assignmentChange) {
        this.assignmentChange = assignmentChange;
    }

    public UUID getPreviousHelperId() {
        return previousHelperId;
    }

    public void setPreviousHelperId(UUID previousHelperId) {
        this.previousHelperId = previousHelperId;
    }

    public UUID getNewHelperId() {
        return newHelperId;
    }

    public void setNewHelperId(UUID newHelperId) {
        this.newHelperId = newHelperId;
    }

    public Integer getPreviousRadiusMeters() {
        return previousRadiusMeters;
    }

    public void setPreviousRadiusMeters(Integer previousRadiusMeters) {
        this.previousRadiusMeters = previousRadiusMeters;
    }

    public Integer getNewRadiusMeters() {
        return newRadiusMeters;
    }

    public void setNewRadiusMeters(Integer newRadiusMeters) {
        this.newRadiusMeters = newRadiusMeters;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
