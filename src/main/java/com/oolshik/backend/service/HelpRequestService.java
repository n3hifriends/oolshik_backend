package com.oolshik.backend.service;

import com.oolshik.backend.config.TaskRecoveryProperties;
import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestCancelReason;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestRejectReason;
import com.oolshik.backend.domain.HelpRequestReleaseReason;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.notification.AssignmentChange;
import com.oolshik.backend.notification.NotificationEventContext;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.HelpRequestRow;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.HelpRequestController;
import com.oolshik.backend.web.dto.HelpRequestDtos;
import com.oolshik.backend.web.error.ForbiddenOperationException;
import com.oolshik.backend.web.error.ConflictOperationException;
import org.apache.coyote.BadRequestException;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class HelpRequestService {

    private final HelpRequestRepository repo;
    private final UserRepository userRepo;
    private final HelpRequestEventService eventService;
    private final TaskRecoveryProperties recoveryProperties;
    private final HelpRequestNotificationService notificationService;
    private final HelpRequestRadiusExpansionService radiusExpansionService;
    private final HelperLocationService helperLocationService;
    private final HelpRequestRatingService ratingService;
    private final HelpRequestCandidateService candidateService;

    public HelpRequestService(
            HelpRequestRepository repo,
            UserRepository userRepo,
            HelpRequestEventService eventService,
            TaskRecoveryProperties recoveryProperties,
            HelpRequestNotificationService notificationService,
            HelpRequestRadiusExpansionService radiusExpansionService,
            HelperLocationService helperLocationService,
            HelpRequestRatingService ratingService,
            HelpRequestCandidateService candidateService
    ) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.eventService = eventService;
        this.recoveryProperties = recoveryProperties;
        this.notificationService = notificationService;
        this.radiusExpansionService = radiusExpansionService;
        this.helperLocationService = helperLocationService;
        this.ratingService = ratingService;
        this.candidateService = candidateService;
    }

    @Transactional
    public HelpRequestEntity create(UUID requesterId, String title, String description, int radiusMeters, String voiceUrl, Point location) {
        UserEntity requester = userRepo.findById(requesterId).orElseThrow(() -> new IllegalArgumentException("Requester not found"));
        boolean titleBlank = (title == null || title.isBlank());
        boolean descriptionBlank = (description == null || description.isBlank());
        boolean hasVoice = voiceUrl != null && !voiceUrl.isBlank();
        if (titleBlank && !hasVoice) {
            throw new IllegalArgumentException("voiceUrl is required when title is empty");
        }

        if (titleBlank) {
            title = "";
        }
        if (descriptionBlank) {
            description = null;
        }
        HelpRequestEntity e = new HelpRequestEntity();
        e.setRequesterId(requester.getId());
        e.setTitle(title);
        e.setDescription(description);
        e.setRadiusMeters(radiusMeters);
        e.setStatus(titleBlank ? HelpRequestStatus.DRAFT : HelpRequestStatus.OPEN);
        e.setVoiceUrl(voiceUrl);
        e.setLocation(location);
        e.setRadiusStage(0);
        if (titleBlank) {
            e.setNextEscalationAt(null);
        } else {
            e.setNextEscalationAt(radiusExpansionService.initialNextEscalationAt(OffsetDateTime.now(), radiusMeters));
        }
        HelpRequestEntity saved = repo.save(e);
        if (saved.getStatus() == HelpRequestStatus.OPEN) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            candidateService.seedCandidatesForNewRequest(saved, now);
            NotificationEventContext context = buildContext(
                    requesterId,
                    null,
                    HelpRequestStatus.OPEN,
                    AssignmentChange.NONE,
                    null,
                    null,
                    0,
                    saved.getRadiusMeters(),
                    now
            );
            notificationService.enqueueTaskEvent(NotificationEventType.TASK_CREATED, saved, context);
        }
        return saved;
    }

    public Page<HelpRequestRow> nearby(
            double lat, double lng, int radiusMeters,
            List<String> statuses, Pageable pageable
    ) {

        String statusesCsv = (statuses == null || statuses.isEmpty())
                ? ""  // value ignored when statusesEmpty = true
                : String.join(",", statuses);
        // call repo:
        return repo.findNearbyPaged(lat, lng, radiusMeters, statusesCsv, pageable);
    }

    public HelpRequestRow findTaskByTaskId(
            UUID taskId
    ) {
        return repo.findTaskByTaskId(taskId);
    }


    @Transactional
    public HelpRequestEntity accept(UUID requestId, UUID helperId, Point acceptorPoint) {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (helperId.equals(e.getRequesterId())) {
            throw new ForbiddenOperationException("Requester can't accept"); // -> 403
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(recoveryProperties.getAuthTtlSeconds());
        int updated = repo.updateAccept(
                requestId,
                helperId,
                acceptorPoint,
                now,
                expiresAt,
                HelpRequestStatus.OPEN,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestEventType.AUTH_REQUESTED.name()
        );
        if (updated == 0) {
            throw new ConflictOperationException("Request not open"); // 409
        }
        eventService.record(
                requestId,
                HelpRequestEventType.AUTH_REQUESTED,
                HelpRequestActorRole.HELPER,
                helperId,
                null,
                null,
                null
        );
        helperLocationService.upsert(helperId, acceptorPoint);
        NotificationEventContext context = buildContext(
                helperId,
                HelpRequestStatus.OPEN,
                HelpRequestStatus.PENDING_AUTH,
                AssignmentChange.ASSIGNED,
                null,
                helperId,
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_AUTH_REQUESTED, e, context);
        return repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    }

    @Transactional
    public HelpRequestEntity authorize(UUID requestId, UUID requesterId) {
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(existing.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can authorize");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(recoveryProperties.getAssignmentTtlSeconds());
        int updated = repo.updateAuthorize(
                requestId,
                requesterId,
                now,
                requesterId,
                expiresAt,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestStatus.ASSIGNED,
                HelpRequestEventType.AUTH_APPROVED.name()
        );
        if (updated == 0) {
            throw new ConflictOperationException("Authorization not allowed");
        }
        eventService.record(
                requestId,
                HelpRequestEventType.AUTH_APPROVED,
                HelpRequestActorRole.REQUESTER,
                requesterId,
                null,
                null,
                null
        );
        NotificationEventContext context = buildContext(
                requesterId,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestStatus.ASSIGNED,
                AssignmentChange.ASSIGNED,
                existing.getPendingHelperId(),
                existing.getPendingHelperId(),
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_AUTH_APPROVED, existing, context);
        return repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    }

    @Transactional
    public HelpRequestEntity reject(UUID requestId, UUID requesterId, HelpRequestDtos.RejectRequest body) {
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(existing.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can reject");
        }
        if (body == null || body.reasonCode() == null) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        HelpRequestRejectReason reason = body.reasonCode();
        String reasonText = body == null ? null : body.reasonText();
        if (reason == HelpRequestRejectReason.OTHER && (reasonText == null || reasonText.isBlank())) {
            throw new IllegalArgumentException("Reason is required when reasonCode is OTHER");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime nextEscalationAt = radiusExpansionService
                .findNextRadius(existing.getRadiusMeters())
                .map(r -> radiusExpansionService.nextEscalationAtForStage(
                        now,
                        (existing.getRadiusStage() == null ? 0 : existing.getRadiusStage()) + 1
                ))
                .orElse(null);
        int updated = repo.updateReject(
                requestId,
                requesterId,
                now,
                requesterId,
                reason == null ? null : reason.name(),
                reasonText,
                nextEscalationAt,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestStatus.OPEN,
                HelpRequestEventType.AUTH_REJECTED.name()
        );
        if (updated == 0) {
            throw new ConflictOperationException("Request not pending authorization");
        }
        eventService.record(
                requestId,
                HelpRequestEventType.AUTH_REJECTED,
                HelpRequestActorRole.REQUESTER,
                requesterId,
                reason == null ? null : reason.name(),
                reasonText,
                null
        );
        NotificationEventContext context = buildContext(
                requesterId,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestStatus.OPEN,
                AssignmentChange.UNASSIGNED,
                existing.getPendingHelperId(),
                null,
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_AUTH_REJECTED, existing, context);
        return repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    }

    @Transactional
    public HelpRequestEntity complete(UUID requestId, UUID requesterId, HelpRequestController.CompletePayload completePayload) throws BadRequestException {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(e.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can complete"); // -> 403
        }
        if (e.getStatus() == HelpRequestStatus.CANCELLED) {
            throw new ConflictOperationException("Request already cancelled");
        }
        e.setStatus(HelpRequestStatus.COMPLETED);
        e.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        e.setNextEscalationAt(null);
        e.setLastStateChangeAt(OffsetDateTime.now(ZoneOffset.UTC));
        e.setLastStateChangeReason(HelpRequestEventType.COMPLETED.name());
        if (completePayload != null && completePayload.rating != null) {
            if (e.getHelperId() == null) {
                throw new BadRequestException("helper not assigned yet");
            }
            ratingService.createRating(
                    requestId,
                    requesterId,
                    e.getHelperId(),
                    HelpRequestActorRole.REQUESTER,
                    completePayload.rating
            );
        }
        eventService.record(
                requestId,
                HelpRequestEventType.COMPLETED,
                HelpRequestActorRole.REQUESTER,
                requesterId,
                null,
                null,
                null
        );
        return repo.save(e);
    }


    @Transactional
    public HelpRequestEntity rate(UUID requestId, UUID requesterId, HelpRequestController.RatePayload ratePayload) throws BadRequestException {

        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (e.getStatus() == HelpRequestStatus.CANCELLED) {
            throw new ConflictOperationException("Request already cancelled");
        }
        boolean isRequester = requesterId.equals(e.getRequesterId());
        boolean isHelper = e.getHelperId() != null && requesterId.equals(e.getHelperId());
        if (!isRequester && !isHelper) {
            throw new ForbiddenOperationException("Only requester or helper can rate"); // -> 403
        }
        if (e.getStatus() != HelpRequestStatus.COMPLETED) {
            throw new ConflictOperationException("Request not completed yet");
        }
        if (ratePayload != null && ratePayload.rating != null) {
            UUID targetUserId = isRequester ? e.getHelperId() : e.getRequesterId();
            if (targetUserId == null) {
                throw new BadRequestException("rating target not found");
            }
            HelpRequestActorRole role = isRequester ? HelpRequestActorRole.REQUESTER : HelpRequestActorRole.HELPER;
            ratingService.createRating(
                    requestId,
                    requesterId,
                    targetUserId,
                    role,
                    ratePayload.rating
            );
        }
        return repo.save(e);
    }

    @Transactional
    public HelpRequestEntity cancel(UUID requestId, UUID requesterId) {
        return cancel(requestId, requesterId, null);
    }

    @Transactional
    public HelpRequestEntity cancel(UUID requestId, UUID requesterId, HelpRequestDtos.CancelRequest body) {
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(existing.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can cancel");
        }
        UUID helperId = existing.getHelperId();
        UUID pendingHelperId = existing.getPendingHelperId();

        HelpRequestCancelReason reason = body == null ? null : body.reasonCode();
        String reasonText = body == null ? null : body.reasonText();
        if (reason == HelpRequestCancelReason.OTHER && (reasonText == null || reasonText.isBlank())) {
            throw new IllegalArgumentException("Reason is required when reasonCode is OTHER");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<HelpRequestStatus> allowed = List.of(
                HelpRequestStatus.OPEN,
                HelpRequestStatus.ASSIGNED,
                HelpRequestStatus.PENDING_AUTH
        );
        int updated = repo.updateCancel(
                requestId,
                requesterId,
                requesterId,
                now,
                reason == null ? null : reason.name(),
                reasonText,
                HelpRequestEventType.CANCELLED.name(),
                allowed,
                HelpRequestStatus.CANCELLED
        );
        if (updated == 0) {
            throw new ConflictOperationException("Request cannot be cancelled");
        }

        eventService.record(
                requestId,
                HelpRequestEventType.CANCELLED,
                HelpRequestActorRole.REQUESTER,
                requesterId,
                reason == null ? null : reason.name(),
                reasonText,
                null
        );
        UUID previousHelperId = helperId != null ? helperId : pendingHelperId;
        AssignmentChange change = previousHelperId == null ? AssignmentChange.NONE : AssignmentChange.UNASSIGNED;
        NotificationEventContext context = buildContext(
                requesterId,
                existing.getStatus(),
                HelpRequestStatus.CANCELLED,
                change,
                previousHelperId,
                null,
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_CANCELLED, existing, context);
        return repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    }

    @Transactional
    public HelpRequestEntity release(UUID requestId, UUID helperId, HelpRequestDtos.ReleaseRequest body) {
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (existing.getHelperId() == null || !helperId.equals(existing.getHelperId())) {
            throw new ForbiddenOperationException("Only assigned helper can release");
        }
        UUID requesterId = existing.getRequesterId();
        HelpRequestReleaseReason reason = body == null ? null : body.reasonCode();
        String reasonText = body == null ? null : body.reasonText();
        if (reason == HelpRequestReleaseReason.OTHER && (reasonText == null || reasonText.isBlank())) {
            throw new IllegalArgumentException("Reason is required when reasonCode is OTHER");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime nextEscalationAt = radiusExpansionService
                .findNextRadius(existing.getRadiusMeters())
                .map(r -> radiusExpansionService.nextEscalationAtForStage(
                        now,
                        (existing.getRadiusStage() == null ? 0 : existing.getRadiusStage()) + 1
                ))
                .orElse(null);
        List<HelpRequestStatus> allowed = List.of(HelpRequestStatus.ASSIGNED);
        int updated = repo.updateRelease(
                requestId,
                helperId,
                now,
                nextEscalationAt,
                HelpRequestEventType.RELEASED.name(),
                allowed,
                HelpRequestStatus.OPEN
        );
        if (updated == 0) {
            throw new ConflictOperationException("Request cannot be released");
        }

        eventService.record(
                requestId,
                HelpRequestEventType.RELEASED,
                HelpRequestActorRole.HELPER,
                helperId,
                reason == null ? null : reason.name(),
                reasonText,
                null
        );
        NotificationEventContext context = buildContext(
                helperId,
                HelpRequestStatus.ASSIGNED,
                HelpRequestStatus.OPEN,
                AssignmentChange.UNASSIGNED,
                helperId,
                null,
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_RELEASED, existing, context);
        return repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    }

    @Transactional
    public HelpRequestEntity reassign(UUID requestId, UUID requesterId) {
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(existing.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can reassign");
        }
        UUID helperId = existing.getHelperId();
        if (existing.getStatus() != HelpRequestStatus.ASSIGNED) {
            throw new ConflictOperationException("Request not assigned");
        }
        if (existing.getHelperAcceptedAt() == null) {
            throw new ConflictOperationException("Request not accepted yet");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime minAcceptedAt = now.minusSeconds(recoveryProperties.getAcceptToStartSlaSeconds());
        OffsetDateTime nextEscalationAt = radiusExpansionService
                .findNextRadius(existing.getRadiusMeters())
                .map(r -> radiusExpansionService.nextEscalationAtForStage(
                        now,
                        (existing.getRadiusStage() == null ? 0 : existing.getRadiusStage()) + 1
                ))
                .orElse(null);
        int updated = repo.updateReassign(
                requestId,
                requesterId,
                now,
                minAcceptedAt,
                recoveryProperties.getMaxReassign(),
                nextEscalationAt,
                HelpRequestStatus.ASSIGNED,
                HelpRequestStatus.OPEN,
                HelpRequestEventType.REASSIGNED.name()
        );
        if (updated == 0) {
            throw new ConflictOperationException("Reassign not allowed yet");
        }

        eventService.record(
                requestId,
                HelpRequestEventType.REASSIGNED,
                HelpRequestActorRole.REQUESTER,
                requesterId,
                "TIMEOUT",
                null,
                null
        );
        NotificationEventContext context = buildContext(
                requesterId,
                HelpRequestStatus.ASSIGNED,
                HelpRequestStatus.OPEN,
                AssignmentChange.UNASSIGNED,
                helperId,
                null,
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_REASSIGNED, existing, context);
        return repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    }

    @Transactional
    public boolean autoReleaseExpired(UUID requestId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        OffsetDateTime nextEscalationAt = radiusExpansionService
                .findNextRadius(existing.getRadiusMeters())
                .map(r -> radiusExpansionService.nextEscalationAtForStage(
                        now,
                        (existing.getRadiusStage() == null ? 0 : existing.getRadiusStage()) + 1
                ))
                .orElse(null);
        int updated = repo.updateAutoRelease(
                requestId,
                now,
                nextEscalationAt,
                HelpRequestStatus.ASSIGNED,
                HelpRequestStatus.OPEN,
                HelpRequestEventType.TIMEOUT.name()
        );
        if (updated == 0) {
            return false;
        }
        eventService.record(
                requestId,
                HelpRequestEventType.TIMEOUT,
                HelpRequestActorRole.SYSTEM,
                null,
                "TIMEOUT",
                null,
                null
        );
        NotificationEventContext context = buildContext(
                null,
                HelpRequestStatus.ASSIGNED,
                HelpRequestStatus.OPEN,
                AssignmentChange.UNASSIGNED,
                existing.getHelperId(),
                null,
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_TIMEOUT, existing, context);
        return true;
    }

    @Transactional
    public boolean autoExpirePendingAuth(UUID requestId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        OffsetDateTime nextEscalationAt = radiusExpansionService
                .findNextRadius(existing.getRadiusMeters())
                .map(r -> radiusExpansionService.nextEscalationAtForStage(
                        now,
                        (existing.getRadiusStage() == null ? 0 : existing.getRadiusStage()) + 1
                ))
                .orElse(null);
        int updated = repo.updateAuthTimeout(
                requestId,
                now,
                nextEscalationAt,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestStatus.OPEN,
                HelpRequestEventType.AUTH_TIMEOUT.name()
        );
        if (updated == 0) {
            return false;
        }
        String metadata = existing.getPendingHelperId() == null
                ? null
                : String.format("{\"pendingHelperId\":\"%s\"}", existing.getPendingHelperId());
        eventService.record(
                requestId,
                HelpRequestEventType.AUTH_TIMEOUT,
                HelpRequestActorRole.SYSTEM,
                null,
                null,
                null,
                metadata
        );
        NotificationEventContext context = buildContext(
                null,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestStatus.OPEN,
                AssignmentChange.UNASSIGNED,
                existing.getPendingHelperId(),
                null,
                null,
                null,
                now
        );
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_AUTH_TIMEOUT, existing, context);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpiredAssignments(OffsetDateTime now, int limit) {
        return repo.findExpiredAssignments(HelpRequestStatus.ASSIGNED, now, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpiredPendingAuth(OffsetDateTime now, int limit) {
        return repo.findExpiredPendingAuth(HelpRequestStatus.PENDING_AUTH, now, PageRequest.of(0, limit));
    }

    private NotificationEventContext buildContext(
            UUID actorUserId,
            HelpRequestStatus previousStatus,
            HelpRequestStatus newStatus,
            AssignmentChange assignmentChange,
            UUID previousHelperId,
            UUID newHelperId,
            Integer previousRadiusMeters,
            Integer newRadiusMeters,
            OffsetDateTime occurredAt
    ) {
        NotificationEventContext context = new NotificationEventContext();
        context.setActorUserId(actorUserId);
        context.setPreviousStatus(previousStatus == null ? null : previousStatus.name());
        context.setNewStatus(newStatus == null ? null : newStatus.name());
        context.setAssignmentChange(assignmentChange == null ? AssignmentChange.NONE : assignmentChange);
        context.setPreviousHelperId(previousHelperId);
        context.setNewHelperId(newHelperId);
        context.setPreviousRadiusMeters(previousRadiusMeters);
        context.setNewRadiusMeters(newRadiusMeters);
        context.setOccurredAt(occurredAt);
        return context;
    }
}
