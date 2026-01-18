package com.oolshik.backend.service;

import com.oolshik.backend.config.TaskRecoveryProperties;
import com.oolshik.backend.domain.HelpRequestCancelReason;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    private TaskRecoveryProperties recoveryProperties;
    private HelpRequestService service;

    @BeforeEach
    void setUp() {
        recoveryProperties = new TaskRecoveryProperties();
        service = new HelpRequestService(repo, userRepo, eventService, recoveryProperties, notificationService);
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
        when(repo.updateReassign(any(), any(), any(), any(), anyInt(), any(), any(), any()))
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

        assertThrows(ForbiddenOperationException.class, () -> service.release(requestId, helperId, null));
    }
}
