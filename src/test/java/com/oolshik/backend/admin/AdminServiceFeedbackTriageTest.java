package com.oolshik.backend.admin;

import com.oolshik.backend.domain.FeedbackContextType;
import com.oolshik.backend.domain.FeedbackPriority;
import com.oolshik.backend.domain.FeedbackStatus;
import com.oolshik.backend.domain.FeedbackType;
import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.FeedbackActionEntity;
import com.oolshik.backend.entity.FeedbackEventEntity;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceFeedbackTriageTest {

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
    void listFeedbackMapsSubmitterAndTriageFields() {
        UUID submitterId = UUID.randomUUID();
        FeedbackEventEntity entity = feedback(UUID.randomUUID(), submitterId);

        when(feedbackEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(submitterId, "Submitter")));

        AdminDtos.PageResponse<AdminDtos.AdminFeedbackRow> page =
                service.getFeedback(null, null, null, null, null, PageRequest.of(0, 20));

        assertEquals(1, page.content().size());
        AdminDtos.AdminFeedbackRow row = page.content().getFirst();
        assertEquals("Submitter", row.submitter().displayName());
        assertEquals("OPEN", row.status());
        assertEquals("MEDIUM", row.priority());
    }

    @Test
    void updateStatusToResolvedSetsResolvedAtAndLogsAction() {
        UUID feedbackId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        FeedbackEventEntity entity = feedback(feedbackId, UUID.randomUUID());

        when(feedbackEventRepository.findById(feedbackId)).thenReturn(Optional.of(entity));
        when(feedbackEventRepository.save(any(FeedbackEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateFeedbackStatus(feedbackId, FeedbackStatus.RESOLVED, "fixed in 2.1", adminId);

        assertEquals(FeedbackStatus.RESOLVED, entity.getStatus());
        assertNotNull(entity.getResolvedAt());
        assertEquals("fixed in 2.1", entity.getResolutionNote());
        verify(feedbackActionRepository).save(any(FeedbackActionEntity.class));
    }

    @Test
    void reopeningClearsResolvedAt() {
        UUID feedbackId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        FeedbackEventEntity entity = feedback(feedbackId, UUID.randomUUID());
        entity.setStatus(FeedbackStatus.RESOLVED);
        entity.setResolvedAt(OffsetDateTime.now());

        when(feedbackEventRepository.findById(feedbackId)).thenReturn(Optional.of(entity));
        when(feedbackEventRepository.save(any(FeedbackEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateFeedbackStatus(feedbackId, FeedbackStatus.OPEN, null, adminId);

        assertEquals(FeedbackStatus.OPEN, entity.getStatus());
        assertNull(entity.getResolvedAt());
    }

    @Test
    void assigningOpenFeedbackMovesItToReviewing() {
        UUID feedbackId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UserEntity admin = user(adminId, "Admin");
        admin.setRoleSet(Set.of(Role.ADMIN));
        FeedbackEventEntity entity = feedback(feedbackId, UUID.randomUUID());

        when(feedbackEventRepository.findById(feedbackId)).thenReturn(Optional.of(entity));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(feedbackEventRepository.save(any(FeedbackEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.assignFeedback(feedbackId, adminId, adminId);

        assertEquals(adminId, entity.getAssignedAdminUserId());
        assertEquals(FeedbackStatus.REVIEWING, entity.getStatus());
    }

    @Test
    void assigningToNonAdminIsRejected() {
        UUID feedbackId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UserEntity nonAdmin = user(assigneeId, "Not Admin");
        nonAdmin.setRoleSet(Set.of(Role.NETA));
        FeedbackEventEntity entity = feedback(feedbackId, UUID.randomUUID());

        when(feedbackEventRepository.findById(feedbackId)).thenReturn(Optional.of(entity));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(nonAdmin));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.assignFeedback(feedbackId, assigneeId, adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void addFeedbackActionDefaultsToNoteWhenActionBlank() {
        UUID feedbackId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        FeedbackEventEntity entity = feedback(feedbackId, UUID.randomUUID());

        when(feedbackEventRepository.findById(feedbackId)).thenReturn(Optional.of(entity));

        service.addFeedbackAction(feedbackId, "  ", "internal note", adminId);

        verify(feedbackActionRepository).save(any(FeedbackActionEntity.class));
    }

    @Test
    void feedbackNotFoundReturns404() {
        UUID feedbackId = UUID.randomUUID();
        when(feedbackEventRepository.findById(feedbackId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateFeedbackStatus(feedbackId, FeedbackStatus.RESOLVED, null, UUID.randomUUID())
        );

        assertEquals(404, ex.getStatusCode().value());
    }

    private FeedbackEventEntity feedback(UUID id, UUID userId) {
        FeedbackEventEntity entity = new FeedbackEventEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setFeedbackType(FeedbackType.BUG);
        entity.setContextType(FeedbackContextType.APP);
        entity.setStatus(FeedbackStatus.OPEN);
        entity.setPriority(FeedbackPriority.MEDIUM);
        entity.setMessage("Something is broken");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setRetentionUntil(OffsetDateTime.now().plusDays(365));
        return entity;
    }

    private UserEntity user(UUID id, String name) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setDisplayName(name);
        user.setPhoneNumber("+919876543210");
        return user;
    }
}
