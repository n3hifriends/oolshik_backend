package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminDtos.RetryTranscriptionResponse;
import com.oolshik.backend.media.AudioPlaybackUrlResolver;
import com.oolshik.backend.payment.PaymentRequestRepository;
import com.oolshik.backend.repo.FeedbackActionRepository;
import com.oolshik.backend.repo.FeedbackEventRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import com.oolshik.backend.repo.OtpAuditLogRepository;
import com.oolshik.backend.repo.PhoneRevealEventRepository;
import com.oolshik.backend.repo.ReportActionRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.service.HelpRequestEventService;
import com.oolshik.backend.service.HelpRequestNotificationService;
import com.oolshik.backend.transcription.TranscriptionJobEntity;
import com.oolshik.backend.transcription.TranscriptionJobPublisher;
import com.oolshik.backend.transcription.TranscriptionJobRepository;
import com.oolshik.backend.transcription.TranscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A one-time Flyway migration (V51) corrects historical stale language hints. Retrying a
 * FAILED job must NOT permanently overwrite its persisted languageHint on every retry --
 * that would silently discard a legitimate explicit hint (e.g. from a future per-recording
 * language picker), contradicting stt-worker/README.md's documented contract that an explicit
 * hint is honored directly.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceRetryTranscriptionsLanguageHintTest {

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
    @Mock private PhoneRevealEventRepository phoneRevealEventRepository;

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
                helpRequestNotificationService,
                phoneRevealEventRepository
        );
    }

    @Test
    void retryDoesNotMutateAnExplicitLanguageHint() {
        TranscriptionJobEntity job = new TranscriptionJobEntity();
        job.setJobId(UUID.randomUUID());
        job.setTaskId(UUID.randomUUID());
        job.setStatus(TranscriptionStatus.FAILED);
        job.setLanguageHint("ta-IN");

        when(transcriptionJobRepository.countByStatus(TranscriptionStatus.FAILED)).thenReturn(1L);
        when(transcriptionJobRepository.findByStatusOrderByUpdatedAtAsc(eq(TranscriptionStatus.FAILED), any()))
                .thenReturn(List.of(job));
        when(transcriptionJobRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RetryTranscriptionResponse response = service.retryFailedTranscriptions();

        assertEquals(1, response.retried());
        assertEquals("ta-IN", job.getLanguageHint());
        assertEquals(TranscriptionStatus.PENDING, job.getStatus());
        verify(transcriptionJobPublisher).publishJob(job);
    }
}
