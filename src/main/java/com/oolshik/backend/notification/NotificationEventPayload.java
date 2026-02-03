package com.oolshik.backend.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public class NotificationEventPayload {

    private UUID eventId;
    private String eventType;
    private UUID taskId;
    private OffsetDateTime occurredAt;
    private UUID actorUserId;
    private UUID requesterUserId;
    private String previousStatus;
    private String newStatus;
    private String assignmentChange;
    private UUID previousHelperId;
    private UUID newHelperId;
    private Geo geo;
    private Integer previousRadiusMeters;
    private Integer newRadiusMeters;

    public static class Geo {
        private Double lat;
        private Double lng;

        public Geo() {}

        public Geo(Double lat, Double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public Double getLat() {
            return lat;
        }

        public void setLat(Double lat) {
            this.lat = lat;
        }

        public Double getLng() {
            return lng;
        }

        public void setLng(Double lng) {
            this.lng = lng;
        }
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public UUID getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(UUID requesterUserId) {
        this.requesterUserId = requesterUserId;
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

    public String getAssignmentChange() {
        return assignmentChange;
    }

    public void setAssignmentChange(String assignmentChange) {
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

    public Geo getGeo() {
        return geo;
    }

    public void setGeo(Geo geo) {
        this.geo = geo;
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
}
