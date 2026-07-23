package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminDtos.AdminFeedbackActionRow;
import com.oolshik.backend.admin.AdminDtos.AdminFeedbackDetail;
import com.oolshik.backend.admin.AdminDtos.AdminFeedbackRow;
import com.oolshik.backend.admin.AdminDtos.AdminNotificationRow;
import com.oolshik.backend.admin.AdminDtos.AdminOtpAuditRow;
import com.oolshik.backend.admin.AdminDtos.AdminPaymentDetail;
import com.oolshik.backend.admin.AdminDtos.AdminPaymentRow;
import com.oolshik.backend.admin.AdminDtos.AdminReportActionRow;
import com.oolshik.backend.admin.AdminDtos.AdminReportDetail;
import com.oolshik.backend.admin.AdminDtos.AdminReportRow;
import com.oolshik.backend.admin.AdminDtos.AdminRequestDetail;
import com.oolshik.backend.admin.AdminDtos.AdminRequestSummary;
import com.oolshik.backend.admin.AdminDtos.AdminTranscriptionRow;
import com.oolshik.backend.admin.AdminDtos.AdminUserDetail;
import com.oolshik.backend.admin.AdminDtos.AdminUserSummary;
import com.oolshik.backend.admin.AdminDtos.GeoPoint;
import com.oolshik.backend.admin.AdminDtos.PageResponse;
import com.oolshik.backend.admin.AdminDtos.RetryTranscriptionResponse;
import com.oolshik.backend.admin.AdminDtos.StatsResponse;
import com.oolshik.backend.admin.AdminDtos.TrendPoint;
import com.oolshik.backend.admin.AdminDtos.UserRef;
import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestCompletionMode;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.media.AudioPlaybackUrlResolver;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.notification.AssignmentChange;
import com.oolshik.backend.notification.NotificationEventContext;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.service.HelpRequestEventService;
import com.oolshik.backend.service.HelpRequestNotificationService;
import com.oolshik.backend.domain.FeedbackContextType;
import com.oolshik.backend.domain.FeedbackPriority;
import com.oolshik.backend.domain.FeedbackStatus;
import com.oolshik.backend.domain.FeedbackType;
import com.oolshik.backend.domain.ReportPriority;
import com.oolshik.backend.domain.ReportReason;
import com.oolshik.backend.domain.ReportStatus;
import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.FeedbackActionEntity;
import com.oolshik.backend.entity.FeedbackEventEntity;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.entity.OtpAuditLogEntity;
import com.oolshik.backend.entity.ReportActionEntity;
import com.oolshik.backend.entity.ReportEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.payment.PaymentMode;
import com.oolshik.backend.payment.PaymentRequest;
import com.oolshik.backend.payment.PaymentRequestRepository;
import com.oolshik.backend.repo.FeedbackActionRepository;
import com.oolshik.backend.repo.FeedbackEventRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import com.oolshik.backend.repo.OtpAuditLogRepository;
import com.oolshik.backend.repo.ReportActionRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.transcription.TranscriptionJobEntity;
import com.oolshik.backend.transcription.TranscriptionJobPublisher;
import com.oolshik.backend.transcription.TranscriptionJobRepository;
import com.oolshik.backend.transcription.TranscriptionStatus;
import com.oolshik.backend.util.PhoneHashUtil;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final int MAX_BLOCK_REASON_LENGTH = 512;

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("NETA", "KARYAKARTA", "ADMIN");
    private static final int MAX_TRANSCRIPTION_RETRY_BATCH = 100;

    private final UserRepository userRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final OtpAuditLogRepository otpAuditLogRepository;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final ReportEventRepository reportEventRepository;
    private final ReportActionRepository reportActionRepository;
    private final FeedbackEventRepository feedbackEventRepository;
    private final FeedbackActionRepository feedbackActionRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final AudioPlaybackUrlResolver audioPlaybackUrlResolver;
    private final TranscriptionJobPublisher transcriptionJobPublisher;
    private final HelpRequestEventService helpRequestEventService;
    private final HelpRequestNotificationService helpRequestNotificationService;

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        long totalUsers = userRepository.count();
        long netas = userRepository.countByRolesContaining("NETA");
        long karyakartas = userRepository.countByRolesContaining("KARYAKARTA");
        long admins = userRepository.countByRolesContaining("ADMIN");

        List<HelpRequestStatus> activeStatuses = List.of(
                HelpRequestStatus.OPEN,
                HelpRequestStatus.PENDING_AUTH,
                HelpRequestStatus.ASSIGNED,
                HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION
        );
        long openRequests = helpRequestRepository.countByStatus(HelpRequestStatus.OPEN);
        long activeRequests = helpRequestRepository.countByStatusIn(activeStatuses);
        long reviewRequired = helpRequestRepository.countByStatus(HelpRequestStatus.REVIEW_REQUIRED);
        long completed = helpRequestRepository.countByStatus(HelpRequestStatus.COMPLETED);
        long sttFailures = transcriptionJobRepository.countByStatus(TranscriptionStatus.FAILED);
        long notificationFailures = notificationOutboxRepository.countUnresolvedFailures();
        long openReports = reportEventRepository.countByStatusIn(List.of(ReportStatus.OPEN, ReportStatus.REVIEWING));
        BigDecimal captured = paymentRequestRepository.sumCapturedAmount();

        return new StatsResponse(
                totalUsers,
                netas,
                karyakartas,
                admins,
                openRequests,
                activeRequests,
                reviewRequired,
                completed,
                sttFailures,
                notificationFailures,
                openReports,
                captured == null ? 0.0 : captured.doubleValue(),
                trend(7)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummary> getUsers(String role, String search, Boolean blocked, Boolean deleted, Pageable pageable) {
        Page<AdminUserSummary> page = userRepository.findForAdmin(blankToNull(role), blankToNull(search), blocked, deleted, pageable)
                .map(this::toUserSummary);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public Optional<AdminUserDetail> getUser(UUID id) {
        return userRepository.findById(id).map(this::toUserDetail);
    }

    @Transactional
    public AdminUserDetail blockUser(UUID targetId, String reason, UUID actingAdminId) {
        if (targetId.equals(actingAdminId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot block your own account.");
        }
        String cleanReason = cleanBlockReason(reason);
        UserEntity target = userRepository.findByIdForUpdate(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getRoleSet().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demote to non-ADMIN before blocking.");
        }
        if (target.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already blocked.");
        }
        target.setBlocked(true);
        target.setBlockedAt(OffsetDateTime.now());
        target.setBlockReason(cleanReason);
        target.setBlockedBy(actingAdminId);
        UserEntity saved = userRepository.save(target);
        log.info("ADMIN_AUDIT: user blocked target={} by admin={} reason={}", targetId, actingAdminId, cleanReason);
        return toUserDetail(saved);
    }

    @Transactional
    public AdminUserDetail unblockUser(UUID targetId, UUID actingAdminId) {
        UserEntity target = userRepository.findByIdForUpdate(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!target.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is not blocked.");
        }
        target.setBlocked(false);
        target.setBlockedAt(null);
        target.setBlockReason(null);
        target.setBlockedBy(null);
        UserEntity saved = userRepository.save(target);
        log.info("ADMIN_AUDIT: user unblocked target={} by admin={}", targetId, actingAdminId);
        return toUserDetail(saved);
    }

    @Transactional
    public AdminUserDetail updateUserRoles(UUID targetId, List<String> requestedRoles, UUID actingAdminId) {
        List<String> newRoles = normalizeRoles(requestedRoles);
        if (newRoles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must retain at least one role.");
        }
        for (String role : newRoles) {
            if (!ALLOWED_ROLES.contains(role)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + role);
            }
        }
        if (targetId.equals(actingAdminId) && !newRoles.contains(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove your own ADMIN role.");
        }

        UserEntity target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean wasAdmin = target.getRoleSet().contains(Role.ADMIN);
        boolean willBeAdmin = newRoles.contains(Role.ADMIN.name());
        if (wasAdmin && !willBeAdmin) {
            List<UserEntity> lockedAdmins = userRepository.lockUsersByRoleForUpdate(Role.ADMIN.name());
            target = lockedAdmins.stream()
                    .filter(user -> user.getId().equals(targetId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "User is no longer an admin."));
            if (lockedAdmins.size() <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last admin.");
            }
        } else {
            target = userRepository.findByIdForUpdate(targetId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }

        String oldRoles = target.getRoles();
        target.setRoles(String.join(",", newRoles));
        UserEntity saved = userRepository.save(target);
        log.info("ADMIN_AUDIT: roles changed for user={} by admin={} from=[{}] to=[{}]",
                targetId, actingAdminId, oldRoles, saved.getRoles());
        return toUserDetail(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminRequestSummary> getUserRequests(UUID userId) {
        return mapRequestSummaries(
                helpRequestRepository.findTop10ByRequesterIdOrHelperIdOrderByLastStateChangeAtDesc(userId, userId)
        );
    }

    @Transactional(readOnly = true)
    public List<AdminOtpAuditRow> getUserOtp(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String phoneHash = PhoneHashUtil.hashPhone(user.getPhoneNumber());
        if (phoneHash == null) {
            return List.of();
        }
        return otpAuditLogRepository.findTop20ByPhoneHashOrderByCreatedAtDesc(phoneHash).stream()
                .map(this::toOtpAuditRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRequestSummary> getRequests(List<HelpRequestStatus> statuses, Pageable pageable) {
        Page<HelpRequestEntity> page = statuses == null || statuses.isEmpty()
                ? helpRequestRepository.findAll(pageable)
                : helpRequestRepository.findByStatusIn(statuses, pageable);
        Map<UUID, UserRef> refs = loadUserRefs(page.getContent().stream()
                .flatMap(request -> userIds(request.getRequesterId(), request.getHelperId()).stream())
                .collect(Collectors.toSet()));
        return PageResponse.from(page.map(request -> toRequestSummary(request, refs)));
    }

    @Transactional(readOnly = true)
    public Optional<AdminRequestDetail> getRequest(UUID id) {
        return helpRequestRepository.findById(id).map(this::toRequestDetail);
    }

    @Transactional
    public AdminRequestDetail adminUpdateHelpRequestStatus(
            UUID requestId, String rawStatus, String note, UUID adminUserId) {
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin note is required");
        }
        HelpRequestStatus newStatus;
        try {
            newStatus = HelpRequestStatus.valueOf(rawStatus == null ? "" : rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + rawStatus);
        }
        if (!Set.of(HelpRequestStatus.OPEN, HelpRequestStatus.REVIEW_REQUIRED,
                HelpRequestStatus.COMPLETED, HelpRequestStatus.CANCELLED).contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status override not supported in v1: " + newStatus);
        }
        HelpRequestEntity entity = helpRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        HelpRequestStatus fromStatus = entity.getStatus();
        if (newStatus == fromStatus) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is already " + newStatus);
        }
        String cleanedNote = cleanNote(note);
        OffsetDateTime now = OffsetDateTime.now();
        switch (newStatus) {
            case OPEN -> {
                entity.setHelperId(null);
                entity.setPendingHelperId(null);
                entity.setPendingAuthExpiresAt(null);
                entity.setAssignmentExpiresAt(null);
                entity.setWorkDoneAt(null);
                entity.setCompletionConfirmationExpiresAt(null);
                entity.setNextEscalationAt(null);
                entity.setCancelledAt(null);
                entity.setCancelledBy(null);
                entity.setCancelReasonCode(null);
                entity.setCancelReasonText(null);
                entity.setCompletedAt(null);
                entity.setCompletionMode(null);
                entity.setCompletedBy(null);
                entity.setIssueReportedAt(null);
                entity.setIssueReasonCode(null);
                entity.setIssueReasonText(null);
                entity.setAcceptedAt(null);
                entity.setAuthorizedAt(null);
                entity.setAuthorizedBy(null);
                entity.setHelperAcceptedAt(null);
                entity.setHelperAcceptLocation(null);
            }
            case REVIEW_REQUIRED -> {
                if (entity.getIssueReportedAt() == null) entity.setIssueReportedAt(now);
                entity.setCompletedAt(null);
                entity.setCompletionMode(null);
                entity.setCompletedBy(null);
                entity.setWorkDoneAt(null);
                entity.setCompletionConfirmationExpiresAt(null);
                entity.setCancelledAt(null);
                entity.setCancelledBy(null);
                entity.setCancelReasonCode(null);
                entity.setCancelReasonText(null);
                entity.setAssignmentExpiresAt(null);
                entity.setNextEscalationAt(null);
            }
            case COMPLETED -> {
                if (entity.getCompletedAt() == null) entity.setCompletedAt(now);
                entity.setCompletionMode(HelpRequestCompletionMode.ADMIN_OVERRIDE);
                entity.setCompletedBy(adminUserId);
                entity.setCompletionConfirmationExpiresAt(null);
                entity.setNextEscalationAt(null);
                entity.setAssignmentExpiresAt(null);
                entity.setCancelledAt(null);
                entity.setCancelledBy(null);
                entity.setCancelReasonCode(null);
                entity.setCancelReasonText(null);
            }
            case CANCELLED -> {
                if (entity.getCancelledAt() == null) entity.setCancelledAt(now);
                entity.setCancelledBy(adminUserId);
                entity.setCancelReasonCode("ADMIN_OVERRIDE");
                entity.setCancelReasonText(cleanedNote);
                entity.setHelperId(null);
                entity.setPendingHelperId(null);
                entity.setPendingAuthExpiresAt(null);
                entity.setAssignmentExpiresAt(null);
                entity.setCompletionConfirmationExpiresAt(null);
                entity.setNextEscalationAt(null);
                entity.setCompletedAt(null);
                entity.setCompletionMode(null);
                entity.setCompletedBy(null);
                entity.setWorkDoneAt(null);
            }
        }
        entity.setStatus(newStatus);
        entity.setLastStateChangeAt(now);
        entity.setLastStateChangeReason("ADMIN_OVERRIDE");
        entity.setAdminOverrideReason(cleanedNote);
        helpRequestRepository.save(entity);
        helpRequestEventService.record(
                requestId,
                HelpRequestEventType.ADMIN_STATUS_OVERRIDE,
                HelpRequestActorRole.ADMIN,
                adminUserId,
                "ADMIN_OVERRIDE",
                cleanedNote,
                null
        );
        if (newStatus == HelpRequestStatus.COMPLETED || newStatus == HelpRequestStatus.CANCELLED) {
            List<com.oolshik.backend.payment.PaymentRequest> activePayments =
                    paymentRequestRepository.findByTaskIdAndStatusInOrderByCreatedAtDesc(
                            requestId, List.of("PENDING", "INITIATED"));
            for (com.oolshik.backend.payment.PaymentRequest payment : activePayments) {
                payment.setStatus("CANCELLED");
                paymentRequestRepository.save(payment);
            }
            if (!activePayments.isEmpty()) {
                log.info("ADMIN_AUDIT: cancelled {} active payment(s) for override id={} status={}",
                        activePayments.size(), requestId, newStatus);
            }
        }
        NotificationEventContext ctx = new NotificationEventContext();
        ctx.setActorUserId(adminUserId);
        ctx.setOccurredAt(now);
        ctx.setPreviousStatus(fromStatus.name());
        ctx.setNewStatus(newStatus.name());
        ctx.setAssignmentChange(AssignmentChange.NONE);
        helpRequestNotificationService.enqueueTaskEvent(
                NotificationEventType.ADMIN_STATUS_OVERRIDE, entity, ctx);
        log.info("ADMIN_AUDIT: help_request status overridden id={} from={} to={} by={}",
                requestId, fromStatus, newStatus, adminUserId);
        return toRequestDetail(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOtpAuditRow> getOtpAudit(String status, Pageable pageable) {
        return PageResponse.from(otpAuditLogRepository.findForAdmin(blankToNull(status), pageable).map(this::toOtpAuditRow));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTranscriptionRow> getTranscriptions(TranscriptionStatus status, Pageable pageable) {
        return PageResponse.from(transcriptionJobRepository.findForAdmin(status, pageable).map(this::toTranscriptionRow));
    }

    @Transactional
    public RetryTranscriptionResponse retryFailedTranscriptions() {
        long totalFailed = transcriptionJobRepository.countByStatus(TranscriptionStatus.FAILED);
        if (totalFailed == 0) {
            return new RetryTranscriptionResponse(0, 0);
        }
        List<TranscriptionJobEntity> failed = transcriptionJobRepository
                .findByStatusOrderByUpdatedAtAsc(
                        TranscriptionStatus.FAILED,
                        PageRequest.of(0, MAX_TRANSCRIPTION_RETRY_BATCH)
                );
        if (failed.isEmpty()) {
            return new RetryTranscriptionResponse(0, (int) Math.min(totalFailed, Integer.MAX_VALUE));
        }

        for (TranscriptionJobEntity job : failed) {
            job.setStatus(TranscriptionStatus.PENDING);
            job.setLastErrorCode(null);
            job.setLastErrorMessage(null);
            job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
        }
        List<TranscriptionJobEntity> saved = transcriptionJobRepository.saveAllAndFlush(failed);
        saved.forEach(transcriptionJobPublisher::publishJob);
        log.info("Admin retried {} of {} failed transcription jobs", saved.size(), totalFailed);
        return new RetryTranscriptionResponse(saved.size(), (int) Math.min(totalFailed, Integer.MAX_VALUE));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentRow> getPayments(String status, PaymentMode mode, Pageable pageable) {
        return PageResponse.from(paymentRequestRepository.findForAdmin(blankToNull(status), mode, pageable).map(this::toPaymentRow));
    }

    @Transactional(readOnly = true)
    public Optional<AdminPaymentDetail> getPaymentDetail(UUID id) {
        return paymentRequestRepository.findById(id).map(pr -> {
            List<UUID> userIds = Stream.of(pr.getPayerUser(), pr.getRequesterUser(), pr.getHelperUser())
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<UUID, UserRef> refs = loadUserRefs(userIds);
            return toPaymentDetail(pr, refs);
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReportRow> getReports(
            ReportStatus status,
            ReportReason reason,
            String targetType,
            String search,
            Pageable pageable) {
        Page<ReportEventEntity> page = reportEventRepository.findAll(
                reportSpec(status, reason, targetType, search),
                pageable
        );
        ReportRefs refs = loadReportRefs(page.getContent());
        return PageResponse.from(page.map(report -> toReportRow(report, refs)));
    }

    @Transactional(readOnly = true)
    public Optional<AdminReportDetail> getReport(UUID id) {
        return reportEventRepository.findById(id).map(report -> {
            ReportRefs refs = loadReportRefs(List.of(report));
            List<ReportActionEntity> actions = reportActionRepository.findTop50ByReportIdOrderByCreatedAtDesc(report.getId());
            Set<UUID> adminIds = actions.stream()
                    .map(ReportActionEntity::getAdminUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, UserRef> actionAdmins = loadUserRefs(adminIds);
            return toReportDetail(report, refs, actions, actionAdmins);
        });
    }

    @Transactional
    public AdminReportDetail updateReportStatus(UUID id, ReportStatus status, String note, UUID adminUserId) {
        ReportEventEntity report = reportEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        ReportStatus fromStatus = report.getStatus();
        report.setStatus(status);
        report.setResolutionNote(cleanNote(note));
        if (status == ReportStatus.RESOLVED || status == ReportStatus.DISMISSED) {
            report.setResolvedAt(OffsetDateTime.now());
        } else {
            report.setResolvedAt(null);
        }
        report = reportEventRepository.save(report);
        saveReportAction(report.getId(), adminUserId, "STATUS_CHANGED", fromStatus, status, note);
        return getReport(report.getId()).orElseThrow();
    }

    @Transactional
    public AdminReportDetail assignReport(UUID id, UUID assigneeId, UUID adminUserId) {
        ReportEventEntity report = reportEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (assigneeId != null) {
            UserEntity assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned admin not found"));
            if (!assignee.getRoleSet().contains(Role.ADMIN)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report assignee must be an admin");
            }
        }
        ReportStatus fromStatus = report.getStatus();
        report.setAssignedAdminUserId(assigneeId);
        if (report.getStatus() == ReportStatus.OPEN && assigneeId != null) {
            report.setStatus(ReportStatus.REVIEWING);
        }
        report = reportEventRepository.save(report);
        saveReportAction(
                report.getId(),
                adminUserId,
                assigneeId == null ? "UNASSIGNED" : "ASSIGNED",
                fromStatus,
                report.getStatus(),
                assigneeId == null ? "Report unassigned" : "Report assigned to " + assigneeId
        );
        return getReport(report.getId()).orElseThrow();
    }

    @Transactional
    public AdminReportDetail addReportAction(UUID id, String action, String note, UUID adminUserId) {
        ReportEventEntity report = reportEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        String cleanAction = action == null || action.isBlank() ? "NOTE" : action.trim().toUpperCase();
        saveReportAction(report.getId(), adminUserId, cleanAction, report.getStatus(), report.getStatus(), note);
        return getReport(report.getId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminFeedbackRow> getFeedback(
            FeedbackType type,
            FeedbackContextType contextType,
            FeedbackStatus status,
            FeedbackPriority priority,
            String search,
            Pageable pageable) {
        Page<FeedbackEventEntity> page = feedbackEventRepository.findAll(
                feedbackSpec(type, contextType, status, priority, search),
                pageable
        );
        FeedbackRefs refs = loadFeedbackRefs(page.getContent());
        return PageResponse.from(page.map(feedback -> toFeedbackRow(feedback, refs)));
    }

    @Transactional(readOnly = true)
    public Optional<AdminFeedbackDetail> getFeedbackItem(UUID id) {
        return feedbackEventRepository.findById(id).map(feedback -> {
            FeedbackRefs refs = loadFeedbackRefs(List.of(feedback));
            List<FeedbackActionEntity> actions = feedbackActionRepository.findTop50ByFeedbackIdOrderByCreatedAtDesc(feedback.getId());
            Set<UUID> adminIds = actions.stream()
                    .map(FeedbackActionEntity::getAdminUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, UserRef> actionAdmins = loadUserRefs(adminIds);
            return toFeedbackDetail(feedback, refs, actions, actionAdmins);
        });
    }

    @Transactional
    public AdminFeedbackDetail updateFeedbackStatus(UUID id, FeedbackStatus status, String note, UUID adminUserId) {
        FeedbackEventEntity feedback = feedbackEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        FeedbackStatus fromStatus = feedback.getStatus();
        feedback.setStatus(status);
        feedback.setResolutionNote(cleanNote(note));
        if (status == FeedbackStatus.RESOLVED || status == FeedbackStatus.DISMISSED) {
            feedback.setResolvedAt(OffsetDateTime.now());
        } else {
            feedback.setResolvedAt(null);
        }
        feedback = feedbackEventRepository.save(feedback);
        saveFeedbackAction(feedback.getId(), adminUserId, "STATUS_CHANGED", fromStatus, status, note);
        return getFeedbackItem(feedback.getId()).orElseThrow();
    }

    @Transactional
    public AdminFeedbackDetail assignFeedback(UUID id, UUID assigneeId, UUID adminUserId) {
        FeedbackEventEntity feedback = feedbackEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        if (assigneeId != null) {
            UserEntity assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned admin not found"));
            if (!assignee.getRoleSet().contains(Role.ADMIN)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback assignee must be an admin");
            }
        }
        FeedbackStatus fromStatus = feedback.getStatus();
        feedback.setAssignedAdminUserId(assigneeId);
        if (feedback.getStatus() == FeedbackStatus.OPEN && assigneeId != null) {
            feedback.setStatus(FeedbackStatus.REVIEWING);
        }
        feedback = feedbackEventRepository.save(feedback);
        saveFeedbackAction(
                feedback.getId(),
                adminUserId,
                assigneeId == null ? "UNASSIGNED" : "ASSIGNED",
                fromStatus,
                feedback.getStatus(),
                assigneeId == null ? "Feedback unassigned" : "Feedback assigned to " + assigneeId
        );
        return getFeedbackItem(feedback.getId()).orElseThrow();
    }

    @Transactional
    public AdminFeedbackDetail addFeedbackAction(UUID id, String action, String note, UUID adminUserId) {
        FeedbackEventEntity feedback = feedbackEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        String cleanAction = action == null || action.isBlank() ? "NOTE" : action.trim().toUpperCase();
        saveFeedbackAction(feedback.getId(), adminUserId, cleanAction, feedback.getStatus(), feedback.getStatus(), note);
        return getFeedbackItem(feedback.getId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationRow> getNotifications(String status, Pageable pageable) {
        return PageResponse.from(notificationOutboxRepository.findForAdmin(blankToNull(status), pageable).map(this::toNotificationRow));
    }

    @Transactional
    public AdminNotificationRow acknowledgeNotification(UUID id, String note, UUID adminUserId) {
        NotificationOutboxEntity outbox = notificationOutboxRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox message not found"));
        if (!"DEAD".equals(outbox.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only dead outbox messages can be acknowledged");
        }
        int updated = notificationOutboxRepository.acknowledgeDead(
                id, adminUserId, cleanNote(note), OffsetDateTime.now());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Outbox message is already acknowledged");
        }
        return toNotificationRow(notificationOutboxRepository.findById(id).orElseThrow());
    }

    @Transactional
    public AdminNotificationRow requeueNotification(UUID id) {
        if (!notificationOutboxRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox message not found");
        }
        int updated = notificationOutboxRepository.requeueFailure(id, OffsetDateTime.now());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed or dead outbox messages can be requeued");
        }
        return toNotificationRow(notificationOutboxRepository.findById(id).orElseThrow());
    }

    private List<TrendPoint> trend(int days) {
        Map<LocalDate, long[]> indexed = helpRequestRepository.findDailyTrend(days).stream()
                .collect(Collectors.toMap(
                        row -> toLocalDate(row[0]),
                        row -> new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()}
                ));
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            long[] values = indexed.getOrDefault(day, new long[]{0L, 0L});
            points.add(new TrendPoint(day.toString(), values[0], values[1]));
        }
        return points;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private AdminUserSummary toUserSummary(UserEntity user) {
        return new AdminUserSummary(
                user.getId(),
                user.getDisplayName(),
                user.getPhoneNumber(),
                user.getEmail(),
                parseRoles(user.getRoles()),
                user.getCreatedAt(),
                user.isBlocked(),
                user.isDeleted()
        );
    }

    private AdminUserDetail toUserDetail(UserEntity user) {
        UserRef blockedByRef = null;
        if (user.getBlockedBy() != null) {
            blockedByRef = userRepository.findById(user.getBlockedBy())
                    .map(u -> new UserRef(u.getId(), u.getDisplayName(), u.getPhoneNumber()))
                    .orElse(null);
        }
        return new AdminUserDetail(
                user.getId(),
                user.getDisplayName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.isEmailVerified(),
                parseRoles(user.getRoles()),
                user.getLanguages(),
                user.getPreferredLanguage(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                helpRequestRepository.countByRequesterId(user.getId()),
                helpRequestRepository.countByHelperIdAndStatus(user.getId(), HelpRequestStatus.COMPLETED),
                user.isBlocked(),
                user.getBlockedAt(),
                user.getBlockReason(),
                blockedByRef,
                user.isDeleted(),
                user.getDeletedAt()
        );
    }

    private List<AdminRequestSummary> mapRequestSummaries(List<HelpRequestEntity> requests) {
        Set<UUID> userIds = requests.stream()
                .flatMap(request -> userIds(request.getRequesterId(), request.getHelperId()).stream())
                .collect(Collectors.toSet());
        Map<UUID, UserRef> refs = loadUserRefs(userIds);
        return requests.stream()
                .map(request -> toRequestSummary(request, refs))
                .toList();
    }

    private AdminRequestSummary toRequestSummary(HelpRequestEntity request) {
        return toRequestSummary(request, loadUserRefs(userIds(request.getRequesterId(), request.getHelperId())));
    }

    private AdminRequestSummary toRequestSummary(HelpRequestEntity request, Map<UUID, UserRef> refs) {
        return new AdminRequestSummary(
                request.getId(),
                request.getTitle(),
                request.getStatus() == null ? null : request.getStatus().name(),
                refs.get(request.getRequesterId()),
                refs.get(request.getHelperId()),
                toGeo(request.getLocation()),
                request.getCreatedAt()
        );
    }

    private AdminRequestDetail toRequestDetail(HelpRequestEntity request) {
        Map<UUID, UserRef> refs = loadUserRefs(userIds(request.getRequesterId(), request.getHelperId()));
        TranscriptionJobEntity transcription = transcriptionJobRepository.findByTaskId(request.getId()).orElse(null);
        String audioUrl = transcription == null
                ? null
                : audioPlaybackUrlResolver.resolve(transcription.getAudioFileId(), transcription.getAudioUrl());
        return new AdminRequestDetail(
                request.getId(),
                request.getTitle(),
                request.getDescription(),
                request.getStatus() == null ? null : request.getStatus().name(),
                refs.get(request.getRequesterId()),
                refs.get(request.getHelperId()),
                toGeo(request.getLocation()),
                request.getRadiusMeters(),
                request.getOfferAmount(),
                request.getOfferCurrency(),
                request.getCreatedAt(),
                audioUrl,
                transcription == null ? null : transcription.getTranscriptText(),
                request.getAdminOverrideReason()
        );
    }

    private AdminOtpAuditRow toOtpAuditRow(OtpAuditLogEntity entity) {
        return new AdminOtpAuditRow(
                entity.getId(),
                entity.getMaskedPhone(),
                entity.getProvider(),
                entity.getAction(),
                entity.getStatus(),
                entity.getDetail(),
                entity.getCreatedAt()
        );
    }

    private AdminTranscriptionRow toTranscriptionRow(TranscriptionJobEntity entity) {
        String audioUrl = audioPlaybackUrlResolver.resolve(entity.getAudioFileId(), entity.getAudioUrl());
        return new AdminTranscriptionRow(
                entity.getJobId(),
                entity.getTaskId(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getEngine(),
                entity.getLanguageHint(),
                entity.getDetectedLanguage(),
                entity.getTranscriptText(),
                entity.getConfidence(),
                entity.getAttemptCount() == null ? 0 : entity.getAttemptCount(),
                entity.getLastErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                audioUrl
        );
    }

    private AdminPaymentRow toPaymentRow(PaymentRequest entity) {
        return new AdminPaymentRow(
                entity.getId(),
                entity.getTaskId(),
                entity.getPayerRole() == null ? null : entity.getPayerRole().name(),
                entity.getAmountRequested(),
                entity.getPaymentMode() == null ? null : entity.getPaymentMode().name(),
                entity.getStatus(),
                entity.getTxnRef(),
                entity.getCreatedAt()
        );
    }

    private AdminPaymentDetail toPaymentDetail(PaymentRequest pr, Map<UUID, UserRef> refs) {
        return new AdminPaymentDetail(
                pr.getId(),
                pr.getTaskId(),
                pr.getPayerRole() == null ? null : pr.getPayerRole().name(),
                pr.getPaymentMode() == null ? null : pr.getPaymentMode().name(),
                pr.getAmountRequested(),
                pr.getCurrency(),
                pr.getStatus(),
                pr.getTxnRef(),
                pr.getPayeeVpa(),
                pr.getPayeeName(),
                pr.getNote(),
                pr.getFormat(),
                refs.get(pr.getPayerUser()),
                refs.get(pr.getRequesterUser()),
                refs.get(pr.getHelperUser()),
                pr.getCreatedAt(),
                pr.getUpdatedAt(),
                pr.getExpiresAt()
        );
    }

    private AdminReportRow toReportRow(ReportEventEntity entity, ReportRefs refs) {
        UUID targetId = reportTargetId(entity);
        String targetType = reportTargetType(entity);
        HelpRequestEntity request = entity.getHelpRequestId() == null ? null : refs.requests().get(entity.getHelpRequestId());
        return new AdminReportRow(
                entity.getId(),
                refs.users().get(entity.getReporterUserId()),
                refs.users().get(entity.getTargetUserId()),
                targetType,
                targetId,
                entity.getReason() == null ? null : entity.getReason().name(),
                entity.getDetails(),
                statusName(entity.getStatus()),
                priorityName(entity.getPriority()),
                refs.users().get(entity.getAssignedAdminUserId()),
                request == null ? null : request.getTitle(),
                request == null || request.getStatus() == null ? null : request.getStatus().name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AdminReportDetail toReportDetail(
            ReportEventEntity entity,
            ReportRefs refs,
            List<ReportActionEntity> actions,
            Map<UUID, UserRef> actionAdmins) {
        UUID targetId = reportTargetId(entity);
        String targetType = reportTargetType(entity);
        HelpRequestEntity request = entity.getHelpRequestId() == null ? null : refs.requests().get(entity.getHelpRequestId());
        return new AdminReportDetail(
                entity.getId(),
                refs.users().get(entity.getReporterUserId()),
                refs.users().get(entity.getTargetUserId()),
                targetType,
                targetId,
                request == null ? null : request.getTitle(),
                request == null || request.getStatus() == null ? null : request.getStatus().name(),
                entity.getReason() == null ? null : entity.getReason().name(),
                entity.getDetails(),
                statusName(entity.getStatus()),
                priorityName(entity.getPriority()),
                refs.users().get(entity.getAssignedAdminUserId()),
                entity.getResolutionNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getResolvedAt(),
                actions.stream()
                        .map(action -> new AdminReportActionRow(
                                action.getId(),
                                actionAdmins.get(action.getAdminUserId()),
                                action.getAction(),
                                action.getFromStatus(),
                                action.getToStatus(),
                                action.getNote(),
                                action.getCreatedAt()
                        ))
                        .toList()
        );
    }

    private String reportTargetType(ReportEventEntity entity) {
        return entity.getHelpRequestId() != null ? "REQUEST" : "USER";
    }

    private UUID reportTargetId(ReportEventEntity entity) {
        return entity.getHelpRequestId() != null ? entity.getHelpRequestId() : entity.getTargetUserId();
    }

    private AdminFeedbackRow toFeedbackRow(FeedbackEventEntity entity, FeedbackRefs refs) {
        return new AdminFeedbackRow(
                entity.getId(),
                refs.users().get(entity.getUserId()),
                entity.getFeedbackType() == null ? null : entity.getFeedbackType().name(),
                entity.getContextType() == null ? null : entity.getContextType().name(),
                entity.getContextId(),
                entity.getRating() == null ? null : entity.getRating().intValue(),
                entity.getTags(),
                entity.getMessage(),
                feedbackStatusName(entity.getStatus()),
                feedbackPriorityName(entity.getPriority()),
                refs.users().get(entity.getAssignedAdminUserId()),
                entity.getAppVersion(),
                entity.getOs(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AdminFeedbackDetail toFeedbackDetail(
            FeedbackEventEntity entity,
            FeedbackRefs refs,
            List<FeedbackActionEntity> actions,
            Map<UUID, UserRef> actionAdmins) {
        HelpRequestEntity task = entity.getContextType() == FeedbackContextType.TASK && entity.getContextId() != null
                ? refs.tasks().get(entity.getContextId())
                : null;
        return new AdminFeedbackDetail(
                entity.getId(),
                refs.users().get(entity.getUserId()),
                entity.getFeedbackType() == null ? null : entity.getFeedbackType().name(),
                entity.getContextType() == null ? null : entity.getContextType().name(),
                entity.getContextId(),
                task == null ? null : task.getTitle(),
                entity.getRating() == null ? null : entity.getRating().intValue(),
                entity.getTags(),
                entity.getMessage(),
                feedbackStatusName(entity.getStatus()),
                feedbackPriorityName(entity.getPriority()),
                refs.users().get(entity.getAssignedAdminUserId()),
                entity.getLocale(),
                entity.getAppVersion(),
                entity.getOs(),
                entity.getDeviceModel(),
                entity.getResolutionNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getResolvedAt(),
                actions.stream()
                        .map(action -> new AdminFeedbackActionRow(
                                action.getId(),
                                actionAdmins.get(action.getAdminUserId()),
                                action.getAction(),
                                action.getFromStatus(),
                                action.getToStatus(),
                                action.getNote(),
                                action.getCreatedAt()
                        ))
                        .toList()
        );
    }

    private Specification<FeedbackEventEntity> feedbackSpec(
            FeedbackType type,
            FeedbackContextType contextType,
            FeedbackStatus status,
            FeedbackPriority priority,
            String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("feedbackType"), type));
            }
            if (contextType != null) {
                predicates.add(cb.equal(root.get("contextType"), contextType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            String cleanSearch = blankToNull(search);
            if (cleanSearch != null) {
                String like = "%" + cleanSearch.toLowerCase() + "%";
                List<jakarta.persistence.criteria.Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(root.get("message")), like));
                try {
                    UUID id = UUID.fromString(cleanSearch);
                    searchPredicates.add(cb.equal(root.get("id"), id));
                    searchPredicates.add(cb.equal(root.get("userId"), id));
                    searchPredicates.add(cb.equal(root.get("contextId"), id));
                } catch (IllegalArgumentException ignored) {
                    // Non-UUID search still covers the message text.
                }
                predicates.add(cb.or(searchPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private FeedbackRefs loadFeedbackRefs(Collection<FeedbackEventEntity> feedbackEvents) {
        Set<UUID> userIds = new LinkedHashSet<>();
        Set<UUID> taskIds = new LinkedHashSet<>();
        for (FeedbackEventEntity feedback : feedbackEvents) {
            if (feedback.getUserId() != null) userIds.add(feedback.getUserId());
            if (feedback.getAssignedAdminUserId() != null) userIds.add(feedback.getAssignedAdminUserId());
            if (feedback.getContextType() == FeedbackContextType.TASK && feedback.getContextId() != null) {
                taskIds.add(feedback.getContextId());
            }
        }
        Map<UUID, HelpRequestEntity> tasks = taskIds.isEmpty()
                ? Map.of()
                : helpRequestRepository.findAllById(taskIds).stream()
                        .collect(Collectors.toMap(HelpRequestEntity::getId, task -> task));
        return new FeedbackRefs(loadUserRefs(userIds), tasks);
    }

    private void saveFeedbackAction(
            UUID feedbackId,
            UUID adminUserId,
            String action,
            FeedbackStatus fromStatus,
            FeedbackStatus toStatus,
            String note) {
        FeedbackActionEntity entity = new FeedbackActionEntity();
        entity.setFeedbackId(feedbackId);
        entity.setAdminUserId(adminUserId);
        entity.setAction(action);
        entity.setFromStatus(feedbackStatusName(fromStatus));
        entity.setToStatus(feedbackStatusName(toStatus));
        entity.setNote(cleanNote(note));
        feedbackActionRepository.save(entity);
    }

    private String feedbackStatusName(FeedbackStatus status) {
        return status == null ? null : status.name();
    }

    private String feedbackPriorityName(FeedbackPriority priority) {
        return priority == null ? null : priority.name();
    }

    private record FeedbackRefs(Map<UUID, UserRef> users, Map<UUID, HelpRequestEntity> tasks) {
    }

    private AdminNotificationRow toNotificationRow(NotificationOutboxEntity entity) {
        return new AdminNotificationRow(
                entity.getId(),
                entity.getEventType(),
                entity.getAggregateId(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getLastError(),
                isNotificationRetryEligible(entity),
                entity.getAcknowledgedAt(),
                entity.getAcknowledgedBy(),
                entity.getResolutionNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean isNotificationRetryEligible(NotificationOutboxEntity entity) {
        if ("DEAD".equals(entity.getStatus())) {
            return true;
        }
        return "FAILED".equals(entity.getStatus())
                && entity.getNextAttemptAt() != null
                && !entity.getNextAttemptAt().isAfter(OffsetDateTime.now());
    }

    private GeoPoint toGeo(Point point) {
        return point == null ? null : new GeoPoint(point.getY(), point.getX());
    }

    private Specification<ReportEventEntity> reportSpec(
            ReportStatus status,
            ReportReason reason,
            String targetType,
            String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            String cleanTargetType = blankToNull(targetType);
            if (cleanTargetType != null) {
                if ("USER".equalsIgnoreCase(cleanTargetType)) {
                    predicates.add(cb.isNull(root.get("helpRequestId")));
                    predicates.add(cb.isNotNull(root.get("targetUserId")));
                } else if ("REQUEST".equalsIgnoreCase(cleanTargetType)) {
                    predicates.add(cb.isNotNull(root.get("helpRequestId")));
                }
            }
            String cleanSearch = blankToNull(search);
            if (cleanSearch != null) {
                String like = "%" + cleanSearch.toLowerCase() + "%";
                List<jakarta.persistence.criteria.Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(root.get("details")), like));
                searchPredicates.add(cb.like(cb.lower(root.get("reason").as(String.class)), like));
                try {
                    UUID id = UUID.fromString(cleanSearch);
                    searchPredicates.add(cb.equal(root.get("id"), id));
                    searchPredicates.add(cb.equal(root.get("reporterUserId"), id));
                    searchPredicates.add(cb.equal(root.get("targetUserId"), id));
                    searchPredicates.add(cb.equal(root.get("helpRequestId"), id));
                } catch (IllegalArgumentException ignored) {
                    // Non-UUID search still covers reason/details.
                }
                predicates.add(cb.or(searchPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private ReportRefs loadReportRefs(Collection<ReportEventEntity> reports) {
        Set<UUID> userIds = new LinkedHashSet<>();
        Set<UUID> requestIds = new LinkedHashSet<>();
        for (ReportEventEntity report : reports) {
            if (report.getReporterUserId() != null) userIds.add(report.getReporterUserId());
            if (report.getTargetUserId() != null) userIds.add(report.getTargetUserId());
            if (report.getAssignedAdminUserId() != null) userIds.add(report.getAssignedAdminUserId());
            if (report.getHelpRequestId() != null) requestIds.add(report.getHelpRequestId());
        }
        Map<UUID, HelpRequestEntity> requests = requestIds.isEmpty()
                ? Map.of()
                : helpRequestRepository.findAllById(requestIds).stream()
                        .collect(Collectors.toMap(HelpRequestEntity::getId, request -> request));
        requests.values().forEach(request -> {
            if (request.getRequesterId() != null) userIds.add(request.getRequesterId());
            if (request.getHelperId() != null) userIds.add(request.getHelperId());
        });
        return new ReportRefs(loadUserRefs(userIds), requests);
    }

    private void saveReportAction(
            UUID reportId,
            UUID adminUserId,
            String action,
            ReportStatus fromStatus,
            ReportStatus toStatus,
            String note) {
        ReportActionEntity entity = new ReportActionEntity();
        entity.setReportId(reportId);
        entity.setAdminUserId(adminUserId);
        entity.setAction(action);
        entity.setFromStatus(statusName(fromStatus));
        entity.setToStatus(statusName(toStatus));
        entity.setNote(cleanNote(note));
        reportActionRepository.save(entity);
    }

    private String statusName(ReportStatus status) {
        return status == null ? null : status.name();
    }

    private String priorityName(ReportPriority priority) {
        return priority == null ? null : priority.name();
    }

    private String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private record ReportRefs(Map<UUID, UserRef> users, Map<UUID, HelpRequestEntity> requests) {
    }

    private Map<UUID, UserRef> loadUserRefs(Collection<UUID> ids) {
        List<UUID> cleanIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (cleanIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(cleanIds).stream()
                .collect(Collectors.toMap(
                        UserEntity::getId,
                        user -> new UserRef(user.getId(), user.getDisplayName(), user.getPhoneNumber()),
                        (left, right) -> left
                ));
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }

    private List<UUID> userIds(UUID first, UUID second) {
        return Arrays.stream(new UUID[]{first, second})
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.trim().toUpperCase())
                .filter(role -> !role.isEmpty())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return "ALL".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private String cleanBlockReason(String reason) {
        String cleaned = blankToNull(reason);
        if (cleaned == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block reason is required.");
        }
        if (cleaned.length() > MAX_BLOCK_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block reason must be 512 characters or fewer.");
        }
        return cleaned;
    }
}
