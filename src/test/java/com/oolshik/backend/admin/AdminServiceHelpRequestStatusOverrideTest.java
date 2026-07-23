package com.oolshik.backend.admin;

import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestCompletionMode;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceHelpRequestStatusOverrideTest {

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
    void openToCompleted_setsCompletionFieldsAndSavesEvent() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, HelpRequestStatus.OPEN);

        stubForUpdate(requestId, entity);
        stubForGetRequest(requestId, entity);

        service.adminUpdateHelpRequestStatus(requestId, "COMPLETED", "Admin closed this task", adminId);

        assertEquals(HelpRequestStatus.COMPLETED, entity.getStatus());
        assertNotNull(entity.getCompletedAt());
        assertEquals(HelpRequestCompletionMode.ADMIN_OVERRIDE, entity.getCompletionMode());
        assertEquals(adminId, entity.getCompletedBy());
        assertNull(entity.getCompletionConfirmationExpiresAt());
        assertNull(entity.getNextEscalationAt());
        assertEquals("ADMIN_OVERRIDE", entity.getLastStateChangeReason());
        assertEquals("Admin closed this task", entity.getAdminOverrideReason());
        verify(helpRequestRepository).save(entity);
        verify(helpRequestEventService).record(eq(requestId), eq(HelpRequestEventType.ADMIN_STATUS_OVERRIDE),
                eq(HelpRequestActorRole.ADMIN), eq(adminId), any(), any(), any());
    }

    @Test
    void assignedToCancelled_setsCancel_clearsHelperFields() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, HelpRequestStatus.ASSIGNED);
        entity.setHelperId(helperId);
        entity.setAssignmentExpiresAt(OffsetDateTime.now().plusHours(1));

        stubForUpdate(requestId, entity);
        stubForGetRequest(requestId, entity);

        service.adminUpdateHelpRequestStatus(requestId, "CANCELLED", "Assigned but no show", adminId);

        assertEquals(HelpRequestStatus.CANCELLED, entity.getStatus());
        assertNotNull(entity.getCancelledAt());
        assertEquals(adminId, entity.getCancelledBy());
        assertEquals("ADMIN_OVERRIDE", entity.getCancelReasonCode());
        assertEquals("Assigned but no show", entity.getCancelReasonText());
        assertNull(entity.getHelperId());
        assertNull(entity.getAssignmentExpiresAt());
        assertNull(entity.getPendingHelperId());
        assertNull(entity.getNextEscalationAt());
        verify(helpRequestRepository).save(entity);
    }

    @Test
    void cancelledToOpen_clearsAllHelperAndCancelFields() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, HelpRequestStatus.CANCELLED);
        entity.setCancelledAt(OffsetDateTime.now().minusHours(1));
        entity.setCancelledBy(UUID.randomUUID());
        entity.setCancelReasonCode("USER_REQUEST");
        entity.setCancelReasonText("Changed mind");

        stubForUpdate(requestId, entity);
        stubForGetRequest(requestId, entity);

        service.adminUpdateHelpRequestStatus(requestId, "OPEN", "Reopening for investigation", adminId);

        assertEquals(HelpRequestStatus.OPEN, entity.getStatus());
        assertNull(entity.getCancelledAt());
        assertNull(entity.getCancelledBy());
        assertNull(entity.getCancelReasonCode());
        assertNull(entity.getCancelReasonText());
        assertNull(entity.getCompletedAt());
        assertNull(entity.getCompletionMode());
        assertNull(entity.getCompletedBy());
        assertNull(entity.getHelperId());
        assertNull(entity.getNextEscalationAt());
        verify(helpRequestRepository).save(entity);
    }

    @Test
    void openToReviewRequired_setsIssueReportedAt() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, HelpRequestStatus.OPEN);

        stubForUpdate(requestId, entity);
        stubForGetRequest(requestId, entity);

        service.adminUpdateHelpRequestStatus(requestId, "REVIEW_REQUIRED", "Suspicious activity", adminId);

        assertEquals(HelpRequestStatus.REVIEW_REQUIRED, entity.getStatus());
        assertNotNull(entity.getIssueReportedAt());
        verify(helpRequestEventService).record(eq(requestId), eq(HelpRequestEventType.ADMIN_STATUS_OVERRIDE),
                eq(HelpRequestActorRole.ADMIN), eq(adminId), any(), any(), any());
    }

    @Test
    void unsupportedTargetStatus_returns400() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.adminUpdateHelpRequestStatus(requestId, "ASSIGNED", "note", adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(helpRequestRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void entityNotFound_returns404() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(helpRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.adminUpdateHelpRequestStatus(requestId, "COMPLETED", "note", adminId)
        );

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void sameStatusAsCurrentTarget_returns400() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HelpRequestEntity entity = request(requestId, HelpRequestStatus.OPEN);
        when(helpRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(entity));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.adminUpdateHelpRequestStatus(requestId, "OPEN", "note", adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void blankNote_returns400BeforeRepoAccess() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.adminUpdateHelpRequestStatus(requestId, "COMPLETED", "  ", adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(helpRequestRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void invalidStatusString_returns400BeforeRepoAccess() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.adminUpdateHelpRequestStatus(requestId, "BOGUS_STATUS", "note", adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(helpRequestRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void nullStatus_returns400BeforeRepoAccess() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.adminUpdateHelpRequestStatus(requestId, null, "note", adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(helpRequestRepository, never()).findByIdForUpdate(any());
    }

    private HelpRequestEntity request(UUID id, HelpRequestStatus status) {
        HelpRequestEntity e = new HelpRequestEntity();
        e.setId(id);
        e.setStatus(status);
        e.setTitle("Test task");
        e.setRequesterId(UUID.randomUUID());
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private void stubForUpdate(UUID requestId, HelpRequestEntity entity) {
        when(helpRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(entity));
        when(helpRequestRepository.save(any(HelpRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubForGetRequest(UUID requestId, HelpRequestEntity entity) {
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(transcriptionJobRepository.findByTaskId(requestId)).thenReturn(Optional.empty());
    }
}
