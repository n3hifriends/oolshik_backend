package com.oolshik.backend.entity;

import com.oolshik.backend.domain.HelpRequestActorRole;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.*;

@Entity
@Table(
        name = "phone_reveal_event",
        indexes = {
                @Index(name = "idx_pre_requester", columnList = "requester_user_id"),
                @Index(name = "idx_pre_target", columnList = "target_user_id"),
                @Index(name = "idx_pre_revealed_at", columnList = "revealed_at"),
                @Index(name = "idx_phone_reveal_event_help_request_id", columnList = "help_request_id")
        }
)
public class PhoneRevealEventEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    /** The user who clicked to reveal the phone number */
    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    /** The owner of the phone number being revealed */
    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    /** The phone number that was revealed (store the exact number shown) */
    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    /** When the reveal happened */
    @Column(name = "revealed_at", nullable = false)
    private Instant revealedAt;

    /** Optional counter if you choose to increment per target/number */
    @Column(name = "reveal_count", nullable = false)
    private Integer revealCount = 0;

    /** The task this reveal happened in. Nullable: historical rows predate this column. */
    @Column(name = "help_request_id")
    private UUID helpRequestId;

    /** The revealer's role on the task at the time of the reveal. */
    @Enumerated(EnumType.STRING)
    @Column(name = "viewer_role", length = 20)
    private HelpRequestActorRole viewerRole;

    /** The target's role on the task at the time of the reveal. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", length = 20)
    private HelpRequestActorRole targetRole;

    /** Where the reveal was triggered from, e.g. MOBILE_TASK_DETAIL. */
    @Column(name = "reveal_source", length = 40)
    private String revealSource;

    /** Masked snapshot of phoneNumber, safe for ordinary admin display. */
    @Column(name = "masked_phone", length = 32)
    private String maskedPhone;

    // ---- Optional read-only associations (handy for joins / projections) ----
    // They are read-only (insertable=false, updatable=false) because we persist via the *_user_id fields.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private UserEntity requesterUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private UserEntity targetUser;

    // ---- Constructors ----
    public PhoneRevealEventEntity() {}

    public PhoneRevealEventEntity(UUID requesterUserId, UUID targetUserId, String phoneNumber) {
        this.requesterUserId = requesterUserId;
        this.targetUserId = targetUserId;
        this.phoneNumber = phoneNumber;
    }

    // ---- Lifecycle ----
    @PrePersist
    public void prePersist() {
        if (this.revealedAt == null) {
            this.revealedAt = Instant.now();
        }
        if (this.revealCount == null) {
            this.revealCount = 0;
        }
    }

    // ---- Getters / Setters ----
    public UUID getId() { return id; }

    public UUID getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(UUID requesterUserId) { this.requesterUserId = requesterUserId; }

    public UUID getTargetUserId() { return targetUserId; }
    public void setTargetUserId(UUID targetUserId) { this.targetUserId = targetUserId; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Instant getRevealedAt() { return revealedAt; }
    public void setRevealedAt(Instant revealedAt) { this.revealedAt = revealedAt; }

    public Integer getRevealCount() { return revealCount; }
    public void setRevealCount(Integer revealCount) { this.revealCount = revealCount; }

    public UUID getHelpRequestId() { return helpRequestId; }
    public void setHelpRequestId(UUID helpRequestId) { this.helpRequestId = helpRequestId; }

    public HelpRequestActorRole getViewerRole() { return viewerRole; }
    public void setViewerRole(HelpRequestActorRole viewerRole) { this.viewerRole = viewerRole; }

    public HelpRequestActorRole getTargetRole() { return targetRole; }
    public void setTargetRole(HelpRequestActorRole targetRole) { this.targetRole = targetRole; }

    public String getRevealSource() { return revealSource; }
    public void setRevealSource(String revealSource) { this.revealSource = revealSource; }

    public String getMaskedPhone() { return maskedPhone; }
    public void setMaskedPhone(String maskedPhone) { this.maskedPhone = maskedPhone; }

    public UserEntity getRequesterUser() { return requesterUser; }
    public UserEntity getTargetUser() { return targetUser; }
}