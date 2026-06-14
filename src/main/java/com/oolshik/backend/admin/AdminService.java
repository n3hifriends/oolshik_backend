package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminDtos.AdminNotificationRow;
import com.oolshik.backend.admin.AdminDtos.AdminOtpAuditRow;
import com.oolshik.backend.admin.AdminDtos.AdminPaymentRow;
import com.oolshik.backend.admin.AdminDtos.AdminReportRow;
import com.oolshik.backend.admin.AdminDtos.AdminRequestDetail;
import com.oolshik.backend.admin.AdminDtos.AdminRequestSummary;
import com.oolshik.backend.admin.AdminDtos.AdminTranscriptionRow;
import com.oolshik.backend.admin.AdminDtos.AdminUserDetail;
import com.oolshik.backend.admin.AdminDtos.AdminUserSummary;
import com.oolshik.backend.admin.AdminDtos.GeoPoint;
import com.oolshik.backend.admin.AdminDtos.PageResponse;
import com.oolshik.backend.admin.AdminDtos.StatsResponse;
import com.oolshik.backend.admin.AdminDtos.TrendPoint;
import com.oolshik.backend.admin.AdminDtos.UserRef;
import com.oolshik.backend.media.AudioPlaybackUrlResolver;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.entity.OtpAuditLogEntity;
import com.oolshik.backend.entity.ReportEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.payment.PaymentMode;
import com.oolshik.backend.payment.PaymentRequest;
import com.oolshik.backend.payment.PaymentRequestRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import com.oolshik.backend.repo.OtpAuditLogRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.transcription.TranscriptionJobEntity;
import com.oolshik.backend.transcription.TranscriptionJobRepository;
import com.oolshik.backend.transcription.TranscriptionStatus;
import com.oolshik.backend.util.PhoneHashUtil;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("NETA", "KARYAKARTA", "ADMIN");

    private final UserRepository userRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final OtpAuditLogRepository otpAuditLogRepository;
    private final TranscriptionJobRepository transcriptionJobRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final ReportEventRepository reportEventRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final AudioPlaybackUrlResolver audioPlaybackUrlResolver;

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
        long notificationFailures = notificationOutboxRepository.countByStatusIn(List.of("FAILED", "DEAD"));
        long openReports = reportEventRepository.count();
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
    public PageResponse<AdminUserSummary> getUsers(String role, String search, Pageable pageable) {
        Page<AdminUserSummary> page = userRepository.findForAdmin(blankToNull(role), blankToNull(search), pageable)
                .map(this::toUserSummary);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public Optional<AdminUserDetail> getUser(UUID id) {
        return userRepository.findById(id).map(this::toUserDetail);
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

    @Transactional(readOnly = true)
    public PageResponse<AdminOtpAuditRow> getOtpAudit(String status, Pageable pageable) {
        return PageResponse.from(otpAuditLogRepository.findForAdmin(blankToNull(status), pageable).map(this::toOtpAuditRow));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTranscriptionRow> getTranscriptions(TranscriptionStatus status, Pageable pageable) {
        return PageResponse.from(transcriptionJobRepository.findForAdmin(status, pageable).map(this::toTranscriptionRow));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentRow> getPayments(String status, PaymentMode mode, Pageable pageable) {
        return PageResponse.from(paymentRequestRepository.findForAdmin(blankToNull(status), mode, pageable).map(this::toPaymentRow));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReportRow> getReports(Pageable pageable) {
        Page<ReportEventEntity> page = reportEventRepository.findAll(pageable);
        Set<UUID> reporterIds = page.getContent().stream()
                .map(ReportEventEntity::getReporterUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserRef> users = loadUserRefs(reporterIds);
        return PageResponse.from(page.map(report -> toReportRow(report, users)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationRow> getNotifications(String status, Pageable pageable) {
        return PageResponse.from(notificationOutboxRepository.findForAdmin(blankToNull(status), pageable).map(this::toNotificationRow));
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
                user.getCreatedAt()
        );
    }

    private AdminUserDetail toUserDetail(UserEntity user) {
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
                helpRequestRepository.countByHelperIdAndStatus(user.getId(), HelpRequestStatus.COMPLETED)
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
                request.getCreatedAt()
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

    private AdminReportRow toReportRow(ReportEventEntity entity, Map<UUID, UserRef> users) {
        UUID targetId = entity.getTargetUserId() != null ? entity.getTargetUserId() : entity.getHelpRequestId();
        String targetType = entity.getTargetUserId() != null ? "USER" : "REQUEST";
        return new AdminReportRow(
                entity.getId(),
                users.get(entity.getReporterUserId()),
                targetType,
                targetId,
                entity.getReason() == null ? null : entity.getReason().name(),
                entity.getDetails(),
                entity.getCreatedAt()
        );
    }

    private AdminNotificationRow toNotificationRow(NotificationOutboxEntity entity) {
        return new AdminNotificationRow(
                entity.getId(),
                entity.getEventType(),
                entity.getAggregateId(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private GeoPoint toGeo(Point point) {
        return point == null ? null : new GeoPoint(point.getY(), point.getX());
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
}
