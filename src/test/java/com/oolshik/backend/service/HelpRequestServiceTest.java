package com.oolshik.backend.service;

import com.oolshik.backend.config.TaskRecoveryProperties;
import com.oolshik.backend.domain.HelpRequestCancelReason;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestRejectReason;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.dto.HelpRequestDtos;
import com.oolshik.backend.web.error.ConflictOperationException;
import com.oolshik.backend.web.error.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

    private TaskRecoveryProperties recoveryProperties;
    private HelpRequestService service;

    @BeforeEach
    void setUp() {
        recoveryProperties = new TaskRecoveryProperties();
        service = new HelpRequestService(
                repo,
                userRepo,
                eventService,
                recoveryProperties,
                notificationService,
                radiusExpansionService,
                helperLocationService
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
}
