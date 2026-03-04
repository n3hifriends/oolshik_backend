package com.oolshik.backend.service;

import com.oolshik.backend.config.TaskRecoveryProperties;
import com.oolshik.backend.domain.HelpRequestActivityPolicy;
import com.oolshik.backend.domain.HelpRequestCancelReason;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestRejectReason;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.HelpRequestOfferEventEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.UserRepository;
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
}
