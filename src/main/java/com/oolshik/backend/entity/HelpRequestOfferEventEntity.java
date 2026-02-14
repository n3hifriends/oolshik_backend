package com.oolshik.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "help_request_offer_events")
public class HelpRequestOfferEventEntity {

    @Id
    private UUID id;

    @Column(name = "help_request_id", nullable = false)
    private UUID helpRequestId;

    @Column(name = "old_amount")
    private BigDecimal oldAmount;

    @Column(name = "new_amount")
    private BigDecimal newAmount;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "event_ts", nullable = false)
    private OffsetDateTime eventTs;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (eventTs == null) {
            eventTs = OffsetDateTime.now();
        }
    }

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

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public void setOldAmount(BigDecimal oldAmount) {
        this.oldAmount = oldAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public void setNewAmount(BigDecimal newAmount) {
        this.newAmount = newAmount;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public OffsetDateTime getEventTs() {
        return eventTs;
    }

    public void setEventTs(OffsetDateTime eventTs) {
        this.eventTs = eventTs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
