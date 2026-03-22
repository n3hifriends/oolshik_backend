package com.oolshik.backend.service;

import com.oolshik.backend.config.TaskRecoveryProperties;
import com.oolshik.backend.domain.HelpRequestActivityPolicy;
import com.oolshik.backend.domain.HelpRequestCancelReason;
import com.oolshik.backend.domain.HelpRequestCompletionMode;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestIssueReason;
import com.oolshik.backend.domain.HelpRequestRejectReason;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.HelpRequestOfferEventEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.HelpRequestController;
import com.oolshik.backend.web.dto.HelpRequestDtos;
import com.oolshik.backend.web.error.ActiveRequestCapReachedException;
import com.oolshik.backend.web.error.ConflictOperationException;
import com.oolshik.backend.web.error.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpRequestServiceTest {

    @Mock
    private HelpRequestRepository repo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private HelpRequestEventService eventService;
    @Mock
    private HelpRequestNotificationService notificationService;
    @Mock
    private HelpRequestRadiusExpansionService radiusExpansionService;
    @Mock
    private HelperLocationService helperLocationService;
    @Mock
    private HelpRequestRatingService ratingService;
    @Mock
    private HelpRequestCandidateService candidateService;
    @Mock
    private com.oolshik.backend.repo.HelpRequestOfferEventRepository offerEventRepository;
    @Mock
    private ActiveRequestCapConfigService activeRequestCapConfigService;

    private TaskRecoveryProperties recoveryProperties;
    private HelpRequestService service;

    @BeforeEach
    void setUp() {
        recoveryProperties = new TaskRecoveryProperties();
        when(activeRequestCapConfigService.getMaxActiveRequestsPerRequester()).thenReturn(2);

        service = new HelpRequestService(
                repo,
                userRepo,
                eventService,
                recoveryProperties,
                notificationService,
                radiusExpansionService,
                helperLocationService,
                ratingService,
                candidateService,
                offerEventRepository,
                activeRequestCapConfigService
        );
    }

    @Test
    void cancelRequiresReasonTextWhenOther() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setStatus(HelpRequestStatus.OPEN);
        when(repo.findById(requestId)).thenReturn(Optional.of(entity));
        when(radiusExpansionService.findNextRadius(anyInt())).thenReturn(Optional.empty());

        HelpRequestDtos.CancelRequest body =
                new HelpRequestDtos.CancelRequest(HelpRequestCancelReason.OTHER, " ");

        assertThrows(IllegalArgumentException.class, () -> service.cancel(requestId, requesterId, body));
    }

    @Test
    void reassignRequiresSla() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setHelperId(helperId);
        entity.setStatus(HelpRequestStatus.ASSIGNED);
        entity.setHelperAcceptedAt(OffsetDateTime.now());

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));
        when(radiusExpansionService.findNextRadius(anyInt())).thenReturn(Optional.empty());
        when(repo.updateReassign(any(), any(), any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThrows(ConflictOperationException.class, () -> service.reassign(requestId, requesterId));
    }

    @Test
    void releaseRequiresAssignedHelper() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setHelperId(UUID.randomUUID());
        entity.setStatus(HelpRequestStatus.ASSIGNED);

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));
        when(radiusExpansionService.findNextRadius(anyInt())).thenReturn(Optional.empty());

        assertThrows(ForbiddenOperationException.class, () -> service.release(requestId, helperId, null));
    }

    @Test
    void acceptTransitionsToPendingAuth() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setStatus(HelpRequestStatus.OPEN);

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));
        when(repo.updateAccept(any(), any(), any(), any(), any(), any(), any(), anyString())).thenReturn(1);

        service.accept(requestId, helperId, null);

        ArgumentCaptor<HelpRequestStatus> statusCaptor = ArgumentCaptor.forClass(HelpRequestStatus.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(repo).updateAccept(any(), any(), any(), any(), any(), statusCaptor.capture(),
                statusCaptor.capture(), reasonCaptor.capture());
        assertEquals(HelpRequestStatus.PENDING_AUTH, statusCaptor.getAllValues().get(1));
        assertEquals(HelpRequestEventType.AUTH_REQUESTED.name(), reasonCaptor.getValue());
    }

    @Test
    void authorizeRequiresRequester() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(UUID.randomUUID());
        entity.setStatus(HelpRequestStatus.PENDING_AUTH);

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));

        assertThrows(ForbiddenOperationException.class, () -> service.authorize(requestId, requesterId));
    }

    @Test
    void rejectRequiresReasonTextWhenOther() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setStatus(HelpRequestStatus.PENDING_AUTH);

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));

        HelpRequestDtos.RejectRequest body =
                new HelpRequestDtos.RejectRequest(HelpRequestRejectReason.OTHER, " ");

        assertThrows(IllegalArgumentException.class, () -> service.reject(requestId, requesterId, body));
    }

    @Test
    void autoExpirePendingAuthReturnsFalseWhenNoUpdate() {
        UUID requestId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setStatus(HelpRequestStatus.PENDING_AUTH);

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));
        when(radiusExpansionService.findNextRadius(anyInt())).thenReturn(Optional.empty());
        when(repo.updateAuthTimeout(any(), any(), any(), any(), any(), anyString())).thenReturn(0);

        assertFalse(service.autoExpirePendingAuth(requestId));
    }


    @Test
    void updateOfferSuppressesNotificationWhenAmountUnchanged() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setStatus(HelpRequestStatus.OPEN);
        entity.setOfferAmount(new BigDecimal("300.00"));
        entity.setOfferCurrency("INR");
        entity.setOfferLastNotifiedAmount(new BigDecimal("300.00"));

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));

        HelpRequestService.OfferUpdateOutcome outcome =
                service.updateOffer(requestId, requesterId, new BigDecimal("300.00"), "INR", "TASK_DETAIL");

        assertTrue(outcome.notificationSuppressed());
        assertFalse(outcome.changed());
        verify(notificationService, never()).enqueueTaskEvent(any(), any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void updateOfferNotifiesWhenChangedAndNotifiedAmountDifferent() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setStatus(HelpRequestStatus.OPEN);
        entity.setOfferAmount(new BigDecimal("250.00"));
        entity.setOfferCurrency("INR");
        entity.setOfferLastNotifiedAmount(new BigDecimal("250.00"));

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerEventRepository.save(any(HelpRequestOfferEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        HelpRequestService.OfferUpdateOutcome outcome =
                service.updateOffer(requestId, requesterId, new BigDecimal("325.00"), "INR", "TASK_DETAIL");

        assertFalse(outcome.notificationSuppressed());
        assertTrue(outcome.changed());
        assertEquals(new BigDecimal("325.00"), outcome.task().getOfferAmount());
        assertEquals(new BigDecimal("325.00"), outcome.task().getOfferLastNotifiedAmount());
        verify(notificationService).enqueueTaskEvent(eq(com.oolshik.backend.notification.NotificationEventType.OFFER_UPDATED), any(), any());
        verify(offerEventRepository).save(any(HelpRequestOfferEventEntity.class));
    }

    @Test
    void updateOfferSuppressesNotificationWhenMatchesLastNotified() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        HelpRequestEntity entity = new HelpRequestEntity();
        entity.setId(requestId);
        entity.setRequesterId(requesterId);
        entity.setStatus(HelpRequestStatus.OPEN);
        entity.setOfferAmount(new BigDecimal("400.00"));
        entity.setOfferCurrency("INR");
        entity.setOfferLastNotifiedAmount(new BigDecimal("250.00"));

        when(repo.findById(requestId)).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerEventRepository.save(any(HelpRequestOfferEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        HelpRequestService.OfferUpdateOutcome outcome =
                service.updateOffer(requestId, requesterId, new BigDecimal("250.00"), "INR", "TASK_DETAIL");

        assertTrue(outcome.notificationSuppressed());
        assertTrue(outcome.changed());
        assertEquals(new BigDecimal("250.00"), outcome.task().getOfferAmount());
        verify(notificationService, never()).enqueueTaskEvent(eq(com.oolshik.backend.notification.NotificationEventType.OFFER_UPDATED), any(), any());
        verify(offerEventRepository).save(any(HelpRequestOfferEventEntity.class));
    }


    @Test
    void createWithOfferEnqueuesInitialTaskCreatedNotification() {
        UUID requesterId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        com.oolshik.backend.entity.UserEntity requester = new com.oolshik.backend.entity.UserEntity();
        requester.setId(requesterId);
        when(userRepo.findByIdForUpdate(requesterId)).thenReturn(Optional.of(requester));
        when(repo.countByRequesterIdAndStatusIn(eq(requesterId), any())).thenReturn(0L);
        when(radiusExpansionService.initialNextEscalationAt(any(), anyInt())).thenReturn(OffsetDateTime.now());
        when(repo.save(any())).thenAnswer(inv -> {
            HelpRequestEntity e = inv.getArgument(0);
            e.setId(taskId);
            return e;
        });

        HelpRequestEntity saved = service.create(
                requesterId,
                "Need help",
                "desc",
                1000,
                "voice",
                null,
                new BigDecimal("250.00"),
                "INR"
        );

        assertEquals(new BigDecimal("250.00"), saved.getOfferAmount());
        assertEquals(new BigDecimal("250.00"), saved.getOfferLastNotifiedAmount());
        verify(candidateService).seedCandidatesForNewRequest(any(), any());
        verify(notificationService).enqueueTaskEvent(eq(com.oolshik.backend.notification.NotificationEventType.TASK_CREATED), any(), any());
    }

    @Test
    void capTwoAllowsTwoCreatesAndBlocksThird() {
        UUID requesterId = UUID.randomUUID();
        com.oolshik.backend.entity.UserEntity requester = new com.oolshik.backend.entity.UserEntity();
        requester.setId(requesterId);

        HelpRequestEntity oldest = new HelpRequestEntity();
        oldest.setId(UUID.randomUUID());
        oldest.setStatus(HelpRequestStatus.OPEN);
        oldest.setCreatedAt(OffsetDateTime.now().minusMinutes(2));

        HelpRequestEntity newest = new HelpRequestEntity();
        newest.setId(UUID.randomUUID());
        newest.setStatus(HelpRequestStatus.ASSIGNED);
        newest.setCreatedAt(OffsetDateTime.now().minusMinutes(1));

        when(userRepo.findByIdForUpdate(requesterId)).thenReturn(Optional.of(requester));
        when(repo.countByRequesterIdAndStatusIn(eq(requesterId), any())).thenReturn(0L, 1L, 2L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(radiusExpansionService.initialNextEscalationAt(any(), anyInt())).thenReturn(OffsetDateTime.now());
        when(repo.findTop10ByRequesterIdAndStatusInOrderByCreatedAtDescIdDesc(eq(requesterId), any()))
                .thenReturn(List.of(newest, oldest));
        when(repo.findFirstByRequesterIdAndStatusInOrderByCreatedAtAscIdAsc(eq(requesterId), any()))
                .thenReturn(Optional.of(oldest));

        service.create(requesterId, "First", "", 1000, null, null, null, null);
        service.create(requesterId, "Second", "", 1000, null, null, null, null);

        ActiveRequestCapReachedException ex = assertThrows(
                ActiveRequestCapReachedException.class,
                () -> service.create(requesterId, "Third", "", 1000, null, null, null, null)
        );

        assertEquals(2, ex.response().cap());
        assertEquals(2, ex.response().activeCount());
        assertEquals(2, ex.response().activeRequestIds().size());
        assertEquals(oldest.getId(), ex.response().suggestedRequestId());
        verify(repo, times(2)).save(any());
        verify(eventService).record(
                eq(oldest.getId()),
                eq(HelpRequestEventType.CREATE_BLOCKED_CAP_REACHED),
                any(),
                eq(requesterId),
                any(),
                any(),
                any()
        );
    }

    @Test
    void draftCreateSkipsCapEnforcement() {
        UUID requesterId = UUID.randomUUID();
        com.oolshik.backend.entity.UserEntity requester = new com.oolshik.backend.entity.UserEntity();
        requester.setId(requesterId);

        when(userRepo.findByIdForUpdate(requesterId)).thenReturn(Optional.of(requester));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HelpRequestEntity saved = service.create(
                requesterId,
                "",
                "voice-first",
                1000,
                "https://example.com/voice.m4a",
                null,
                null,
                null
        );

        assertEquals(HelpRequestStatus.DRAFT, saved.getStatus());
        verify(repo, never()).countByRequesterIdAndStatusIn(eq(requesterId), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void capCheckUsesOnlyActiveStatuses() {
        UUID requesterId = UUID.randomUUID();
        com.oolshik.backend.entity.UserEntity requester = new com.oolshik.backend.entity.UserEntity();
        requester.setId(requesterId);

        when(userRepo.findByIdForUpdate(requesterId)).thenReturn(Optional.of(requester));
        when(repo.countByRequesterIdAndStatusIn(eq(requesterId), any())).thenReturn(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(radiusExpansionService.initialNextEscalationAt(any(), anyInt())).thenReturn(OffsetDateTime.now());

        service.create(requesterId, "Need help", "", 1000, null, null, null, null);

        ArgumentCaptor<Collection<HelpRequestStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(repo).countByRequesterIdAndStatusIn(eq(requesterId), statusesCaptor.capture());

        Collection<HelpRequestStatus> statuses = statusesCaptor.getValue();
        assertTrue(statuses.containsAll(HelpRequestActivityPolicy.activeStatuses()));
        assertFalse(statuses.contains(HelpRequestStatus.COMPLETED));
        assertFalse(statuses.contains(HelpRequestStatus.CANCELLED));
        assertFalse(statuses.contains(HelpRequestStatus.DRAFT));
    }

    @Test
    void markDoneTransitionsAssignedTaskToPendingConfirmation() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(requesterId);
        existing.setHelperId(helperId);
        existing.setStatus(HelpRequestStatus.ASSIGNED);

        HelpRequestEntity updated = new HelpRequestEntity();
        updated.setId(requestId);
        updated.setRequesterId(requesterId);
        updated.setHelperId(helperId);
        updated.setStatus(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing), Optional.of(updated));
        when(repo.updateMarkDoneIfAssigned(
                eq(requestId),
                eq(helperId),
                any(),
                any(),
                eq(HelpRequestStatus.ASSIGNED),
                eq(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION),
                eq(HelpRequestEventType.WORK_MARKED_DONE.name())
        )).thenReturn(1);

        HelpRequestEntity result = service.markDone(requestId, helperId);

        assertEquals(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION, result.getStatus());
        verify(eventService).record(
                eq(requestId),
                eq(HelpRequestEventType.WORK_MARKED_DONE),
                eq(com.oolshik.backend.domain.HelpRequestActorRole.HELPER),
                eq(helperId),
                eq(null),
                eq(null),
                eq(null)
        );
        verify(notificationService).enqueueTaskEvent(
                eq(com.oolshik.backend.notification.NotificationEventType.WORK_MARKED_DONE),
                eq(existing),
                any()
        );
    }

    @Test
    void markDoneRejectsNonAssignedHelper() {
        UUID requestId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(UUID.randomUUID());
        existing.setHelperId(UUID.randomUUID());
        existing.setStatus(HelpRequestStatus.ASSIGNED);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing));

        assertThrows(ForbiddenOperationException.class, () -> service.markDone(requestId, helperId));
        verifyNoInteractions(eventService, notificationService);
    }

    @Test
    void confirmCompletionTransitionsPendingTaskToCompleted() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(requesterId);
        existing.setHelperId(helperId);
        existing.setStatus(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION);

        HelpRequestEntity updated = new HelpRequestEntity();
        updated.setId(requestId);
        updated.setRequesterId(requesterId);
        updated.setHelperId(helperId);
        updated.setStatus(HelpRequestStatus.COMPLETED);
        updated.setCompletionMode(HelpRequestCompletionMode.REQUESTER_CONFIRMED);
        updated.setCompletedBy(requesterId);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing), Optional.of(updated));
        when(repo.updateConfirmCompletionIfPending(
                eq(requestId),
                eq(requesterId),
                any(),
                eq(requesterId),
                eq(HelpRequestCompletionMode.REQUESTER_CONFIRMED),
                eq(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION),
                eq(HelpRequestStatus.COMPLETED),
                eq(HelpRequestEventType.COMPLETION_CONFIRMED.name())
        )).thenReturn(1);

        HelpRequestEntity result = service.confirmCompletion(requestId, requesterId);

        assertEquals(HelpRequestStatus.COMPLETED, result.getStatus());
        assertEquals(HelpRequestCompletionMode.REQUESTER_CONFIRMED, result.getCompletionMode());
        verify(eventService).record(
                eq(requestId),
                eq(HelpRequestEventType.COMPLETION_CONFIRMED),
                eq(com.oolshik.backend.domain.HelpRequestActorRole.REQUESTER),
                eq(requesterId),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    void reportIssueTransitionsPendingTaskToReviewRequired() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(requesterId);
        existing.setHelperId(helperId);
        existing.setStatus(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION);

        HelpRequestEntity updated = new HelpRequestEntity();
        updated.setId(requestId);
        updated.setRequesterId(requesterId);
        updated.setHelperId(helperId);
        updated.setStatus(HelpRequestStatus.REVIEW_REQUIRED);
        updated.setIssueReasonCode(HelpRequestIssueReason.QUALITY_ISSUE);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing), Optional.of(updated));
        when(repo.updateReportIssueIfPending(
                eq(requestId),
                eq(requesterId),
                any(),
                eq(HelpRequestIssueReason.QUALITY_ISSUE),
                eq("Need cleanup"),
                eq(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION),
                eq(HelpRequestStatus.REVIEW_REQUIRED),
                eq(HelpRequestEventType.COMPLETION_ISSUE_REPORTED.name())
        )).thenReturn(1);

        HelpRequestEntity result = service.reportIssue(
                requestId,
                requesterId,
                new HelpRequestDtos.ReportIssueRequest(HelpRequestIssueReason.QUALITY_ISSUE, "Need cleanup")
        );

        assertEquals(HelpRequestStatus.REVIEW_REQUIRED, result.getStatus());
        assertEquals(HelpRequestIssueReason.QUALITY_ISSUE, result.getIssueReasonCode());
        verify(eventService).record(
                eq(requestId),
                eq(HelpRequestEventType.COMPLETION_ISSUE_REPORTED),
                eq(com.oolshik.backend.domain.HelpRequestActorRole.REQUESTER),
                eq(requesterId),
                eq(HelpRequestIssueReason.QUALITY_ISSUE.name()),
                eq("Need cleanup"),
                eq(null)
        );
    }

    @Test
    void reportIssueRequiresReasonTextForOther() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(requesterId);
        existing.setHelperId(UUID.randomUUID());
        existing.setStatus(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reportIssue(
                        requestId,
                        requesterId,
                        new HelpRequestDtos.ReportIssueRequest(HelpRequestIssueReason.OTHER, " ")
                )
        );
    }

    @Test
    void autoCompletePendingConfirmationReturnsTrueWhenUpdated() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(requesterId);
        existing.setHelperId(helperId);
        existing.setStatus(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing), Optional.of(existing));
        when(repo.updateAutoCompleteIfExpired(
                eq(requestId),
                any(),
                eq(HelpRequestCompletionMode.AUTO_TIMEOUT),
                eq(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION),
                eq(HelpRequestStatus.COMPLETED),
                eq(HelpRequestEventType.AUTO_COMPLETED_BY_TIMEOUT.name())
        )).thenReturn(1);

        assertTrue(service.autoCompletePendingConfirmation(requestId));
        verify(notificationService).enqueueTaskEvent(
                eq(com.oolshik.backend.notification.NotificationEventType.AUTO_COMPLETED_BY_TIMEOUT),
                eq(existing),
                any()
        );
    }

    @Test
    void sendCompletionReminder50IsDeduplicatedByConditionalUpdate() {
        UUID requestId = UUID.randomUUID();
        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(UUID.randomUUID());
        existing.setHelperId(UUID.randomUUID());
        existing.setStatus(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing));
        when(repo.markReminder50Sent(eq(requestId), any(), eq(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION)))
                .thenReturn(0);

        assertFalse(service.sendCompletionReminder50(requestId));
        verify(notificationService, never()).enqueueTaskEvent(
                eq(com.oolshik.backend.notification.NotificationEventType.COMPLETION_REMINDER_50),
                any(),
                any()
        );
    }

    @Test
    void rateRejectsBeforeFinalCompletion() {
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity existing = new HelpRequestEntity();
        existing.setId(requestId);
        existing.setRequesterId(requesterId);
        existing.setHelperId(helperId);
        existing.setStatus(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION);

        when(repo.findById(requestId)).thenReturn(Optional.of(existing));

        assertThrows(
                ConflictOperationException.class,
                () -> service.rate(requestId, requesterId, new HelpRequestController.RatePayload())
        );
    }

    @Test
    void reminderCandidateLookupUsesConfiguredThreshold() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-21T12:00:00Z");
        UUID requestId = UUID.randomUUID();

        when(repo.findReminder50Candidates(eq(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION), any(), any()))
                .thenReturn(List.of(requestId));

        List<UUID> result = service.findCompletionReminder50Candidates(now, 10);

        assertEquals(List.of(requestId), result);
        verify(repo).findReminder50Candidates(
                eq(HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION),
                eq(now.minusHours(6)),
                any()
        );
    }
}
