package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminDtos.AdminEventRow;
import com.oolshik.backend.admin.AdminDtos.AdminRequestDetail;
import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.HelpRequestEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.media.AudioPlaybackUrlResolver;
import com.oolshik.backend.payment.PaymentRequestRepository;
import com.oolshik.backend.repo.FeedbackActionRepository;
import com.oolshik.backend.repo.FeedbackEventRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import com.oolshik.backend.repo.OtpAuditLogRepository;
import com.oolshik.backend.repo.ReportActionRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.service.HelpRequestEventService;
import com.oolshik.backend.service.HelpRequestNotificationService;
import com.oolshik.backend.transcription.TranscriptionJobPublisher;
import com.oolshik.backend.transcription.TranscriptionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceRequestEventsTest {

    @Mock private UserRepository userRepository;
    @Mock private HelpRequestRepository helpRequestRepository;
    @Mock private OtpAuditLogRepository otpAuditLogRepository;
    @Mock private TranscriptionJobRepository transcriptionJobRepository;
    @Mock private PaymentRequestRepository paymentRequestRepository;
    @Mock private ReportEventRepository reportEventRepository;
    @Mock private ReportActionRepository reportActionRepository;
    @Mock private FeedbackEventRepository feedbackEventRepository;
    @Mock private FeedbackActionRepository feedbackActionRepository;
    @Mock private NotificationOutboxRepository notificationOutboxRepository;
    @Mock private AudioPlaybackUrlResolver audioPlaybackUrlResolver;
    @Mock private TranscriptionJobPublisher transcriptionJobPublisher;
    @Mock private HelpRequestEventService helpRequestEventService;
    @Mock private HelpRequestNotificationService helpRequestNotificationService;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(
                userRepository,
                helpRequestRepository,
                otpAuditLogRepository,
                transcriptionJobRepository,
                paymentRequestRepository,
                reportEventRepository,
                reportActionRepository,
                feedbackEventRepository,
                feedbackActionRepository,
                notificationOutboxRepository,
                audioPlaybackUrlResolver,
                transcriptionJobPublisher,
                helpRequestEventService,
                helpRequestNotificationService
        );
    }

    @Test
    void getRequest_mapsReassignEventWithResolvedActorNameAndReason() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, requesterId);

        when(helpRequestRepository.findById(requestId)).thenReturn(Optional.of(entity));
        when(transcriptionJobRepository.findByTaskId(requestId)).thenReturn(Optional.empty());
        when(userRepository.findAllById(any())).thenReturn(List.of(user(requesterId, "Asha Patil")));

        OffsetDateTime eventAt = OffsetDateTime.now();
        HelpRequestEventEntity event = event(
                requestId, HelpRequestEventType.REASSIGNED, HelpRequestActorRole.REQUESTER,
                requesterId, "HELPER_NOT_RESPONDING", null, eventAt
        );
        when(helpRequestEventService.listForRequest(requestId)).thenReturn(List.of(event));

        AdminRequestDetail detail = service.getRequest(requestId).orElseThrow();

        assertEquals(1, detail.events().size());
        AdminEventRow row = detail.events().get(0);
        assertEquals("REASSIGNED", row.kind());
        assertEquals("Asha Patil", row.by());
        assertEquals("HELPER_NOT_RESPONDING", row.reasonCode());
        assertEquals(eventAt, row.at());
        assertEquals("Requester reassigned task — Helper Not Responding", row.label());
    }

    @Test
    void getRequest_othersReasonUsesFreeTextInLabel() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, requesterId);

        when(helpRequestRepository.findById(requestId)).thenReturn(Optional.of(entity));
        when(transcriptionJobRepository.findByTaskId(requestId)).thenReturn(Optional.empty());
        when(userRepository.findAllById(any())).thenReturn(List.of(user(requesterId, "Asha Patil")));

        HelpRequestEventEntity event = event(
                requestId, HelpRequestEventType.CANCELLED, HelpRequestActorRole.REQUESTER,
                requesterId, "OTHER", "Booked a different service", OffsetDateTime.now()
        );
        when(helpRequestEventService.listForRequest(requestId)).thenReturn(List.of(event));

        AdminRequestDetail detail = service.getRequest(requestId).orElseThrow();

        assertEquals(
                "Requester cancelled task — Booked a different service",
                detail.events().get(0).label()
        );
    }

    @Test
    void getRequest_unresolvedActorFallsBackToRoleLabel() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, requesterId);

        when(helpRequestRepository.findById(requestId)).thenReturn(Optional.of(entity));
        when(transcriptionJobRepository.findByTaskId(requestId)).thenReturn(Optional.empty());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        HelpRequestEventEntity event = event(
                requestId, HelpRequestEventType.ADMIN_STATUS_OVERRIDE, HelpRequestActorRole.ADMIN,
                UUID.randomUUID(), null, null, OffsetDateTime.now()
        );
        when(helpRequestEventService.listForRequest(requestId)).thenReturn(List.of(event));

        AdminRequestDetail detail = service.getRequest(requestId).orElseThrow();

        AdminEventRow row = detail.events().get(0);
        assertEquals("Admin", row.by());
        assertTrue(row.label().startsWith("Admin overrode status"));
    }

    private HelpRequestEntity request(UUID id, UUID requesterId) {
        HelpRequestEntity e = new HelpRequestEntity();
        e.setId(id);
        e.setStatus(HelpRequestStatus.OPEN);
        e.setTitle("Test task");
        e.setRequesterId(requesterId);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private UserEntity user(UUID id, String displayName) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setDisplayName(displayName);
        return u;
    }

    private HelpRequestEventEntity event(
            UUID requestId,
            HelpRequestEventType type,
            HelpRequestActorRole role,
            UUID actorUserId,
            String reasonCode,
            String reasonText,
            OffsetDateTime at
    ) {
        HelpRequestEventEntity e = new HelpRequestEventEntity();
        e.setId(UUID.randomUUID());
        e.setRequestId(requestId);
        e.setEventType(type);
        e.setActorRole(role);
        e.setActorUserId(actorUserId);
        e.setReasonCode(reasonCode);
        e.setReasonText(reasonText);
        e.setCreatedAt(at);
        return e;
    }
}
