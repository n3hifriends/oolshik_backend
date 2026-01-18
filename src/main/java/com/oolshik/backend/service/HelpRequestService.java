package com.oolshik.backend.service;

import com.oolshik.backend.config.TaskRecoveryProperties;
import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestCancelReason;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestReleaseReason;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.UserEntity;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public HelpRequestService(
            HelpRequestRepository repo,
            UserRepository userRepo,
            HelpRequestEventService eventService,
            TaskRecoveryProperties recoveryProperties,
            HelpRequestNotificationService notificationService
    ) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.eventService = eventService;
        this.recoveryProperties = recoveryProperties;
        this.notificationService = notificationService;
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
        return repo.save(e);
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
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusSeconds(recoveryProperties.getAssignmentTtlSeconds());
        int updated = repo.updateAccept(
                requestId,
                helperId,
                acceptorPoint,
                now,
                expiresAt,
                HelpRequestStatus.OPEN,
                HelpRequestStatus.ASSIGNED,
                HelpRequestEventType.ACCEPTED.name()
        );
        if (updated == 0) {
            throw new ConflictOperationException("Request not open"); // 409
        }
        eventService.record(
                requestId,
                HelpRequestEventType.ACCEPTED,
                HelpRequestActorRole.HELPER,
                helperId,
                null,
                null,
                null
        );
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
        e.setLastStateChangeAt(OffsetDateTime.now(ZoneOffset.UTC));
        e.setLastStateChangeReason(HelpRequestEventType.COMPLETED.name());
        if (completePayload != null && completePayload.rating != null) {
            applyRating(e, requesterId, completePayload.rating, completePayload.feedback);
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
        if (!requesterId.equals(e.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can complete"); // -> 403
        }
        if (e.getStatus() == HelpRequestStatus.CANCELLED) {
            throw new ConflictOperationException("Request already cancelled");
        }
        e.setStatus(HelpRequestStatus.COMPLETED);
        e.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        e.setLastStateChangeAt(OffsetDateTime.now(ZoneOffset.UTC));
        e.setLastStateChangeReason(HelpRequestEventType.COMPLETED.name());
        if (ratePayload != null && ratePayload.rating != null) {
            applyRating(e, requesterId, ratePayload.rating, ratePayload.feedback);
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

    private void applyRating(HelpRequestEntity hr, UUID actorUserId, BigDecimal rating, String feedback) throws BadRequestException {
        BigDecimal r = rating.setScale(1, RoundingMode.HALF_UP);
        if (r.compareTo(new BigDecimal("0.0")) < 0 || r.compareTo(new BigDecimal("5.0")) > 0)
            throw new BadRequestException("rating must be between 0.0 and 5.0");

        // Optional: only requester can rate helper (or vice versa). Adjust as per your rule.
        // e.g., require actorUserId equals hr.getRequesterId() OR hr.getHelperId()

        // Prevent double-rating
        if (hr.getRatingValue() != null)
            throw new BadRequestException("rating already submitted");

        hr.setRatingValue(r);
        hr.setRatedByUserId(actorUserId);
        hr.setRatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // TODO: store feedback somewhere if/when required (another column/table)
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

        HelpRequestCancelReason reason = body == null ? null : body.reasonCode();
        String reasonText = body == null ? null : body.reasonText();
        if (reason == HelpRequestCancelReason.OTHER && (reasonText == null || reasonText.isBlank())) {
            throw new IllegalArgumentException("Reason is required when reasonCode is OTHER");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<HelpRequestStatus> allowed = List.of(HelpRequestStatus.OPEN, HelpRequestStatus.ASSIGNED);
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
        if (helperId != null) {
            notificationService.notifyHelper(helperId, "TASK_CANCELLED", requestId);
        }
        notificationService.notifyRequester(requesterId, "TASK_CANCELLED_CONFIRM", requestId);
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
        List<HelpRequestStatus> allowed = List.of(HelpRequestStatus.ASSIGNED);
        int updated = repo.updateRelease(
                requestId,
                helperId,
                now,
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
        notificationService.notifyRequester(requesterId, "TASK_RELEASED", requestId);
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
        int updated = repo.updateReassign(
                requestId,
                requesterId,
                now,
                minAcceptedAt,
                recoveryProperties.getMaxReassign(),
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
        if (helperId != null) {
            notificationService.notifyHelper(helperId, "TASK_REASSIGNED", requestId);
        }
        notificationService.notifyRequester(requesterId, "TASK_REOPENED", requestId);
        return repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    }

    @Transactional
    public boolean autoReleaseExpired(UUID requestId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        HelpRequestEntity existing = repo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        int updated = repo.updateAutoRelease(
                requestId,
                now,
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
        if (existing.getHelperId() != null) {
            notificationService.notifyHelper(existing.getHelperId(), "TASK_TIMEOUT_REOPENED", requestId);
        }
        notificationService.notifyRequester(existing.getRequesterId(), "TASK_TIMEOUT_REOPENED", requestId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpiredAssignments(OffsetDateTime now, int limit) {
        return repo.findExpiredAssignments(HelpRequestStatus.ASSIGNED, now, PageRequest.of(0, limit));
    }
}
