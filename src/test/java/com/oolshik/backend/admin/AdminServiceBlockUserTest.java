package com.oolshik.backend.admin;

import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.media.AudioPlaybackUrlResolver;
import com.oolshik.backend.payment.PaymentRequestRepository;
import com.oolshik.backend.repo.FeedbackActionRepository;
import com.oolshik.backend.repo.FeedbackEventRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.service.HelpRequestEventService;
import com.oolshik.backend.service.HelpRequestNotificationService;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import com.oolshik.backend.repo.OtpAuditLogRepository;
import com.oolshik.backend.repo.ReportActionRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.transcription.TranscriptionJobPublisher;
import com.oolshik.backend.transcription.TranscriptionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceBlockUserTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HelpRequestRepository helpRequestRepository;
    @Mock
    private OtpAuditLogRepository otpAuditLogRepository;
    @Mock
    private TranscriptionJobRepository transcriptionJobRepository;
    @Mock
    private PaymentRequestRepository paymentRequestRepository;
    @Mock
    private ReportEventRepository reportEventRepository;
    @Mock
    private ReportActionRepository reportActionRepository;
    @Mock
    private FeedbackEventRepository feedbackEventRepository;
    @Mock
    private FeedbackActionRepository feedbackActionRepository;
    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;
    @Mock
    private AudioPlaybackUrlResolver audioPlaybackUrlResolver;
    @Mock
    private TranscriptionJobPublisher transcriptionJobPublisher;
    @Mock
    private HelpRequestEventService helpRequestEventService;
    @Mock
    private HelpRequestNotificationService helpRequestNotificationService;

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
    void adminCanBlockUserById() {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UserEntity target = user(targetId, "Target", Role.NETA);
        UserEntity admin = user(adminId, "Admin", Role.ADMIN);

        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        AdminDtos.AdminUserDetail detail = service.blockUser(targetId, "Policy violation", adminId);

        assertTrue(target.isBlocked());
        assertNotNull(target.getBlockedAt());
        assertEquals("Policy violation", target.getBlockReason());
        assertEquals(adminId, target.getBlockedBy());
        assertTrue(detail.blocked());
        assertEquals("Policy violation", detail.blockReason());
        assertEquals(adminId, detail.blockedBy().id());
        verify(userRepository).save(target);
    }

    @Test
    void adminCanUnblockBlockedUser() {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UserEntity target = user(targetId, "Target", Role.NETA);
        target.setBlocked(true);
        target.setBlockedAt(OffsetDateTime.now());
        target.setBlockReason("Policy violation");
        target.setBlockedBy(adminId);

        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDtos.AdminUserDetail detail = service.unblockUser(targetId, adminId);

        assertFalse(target.isBlocked());
        assertNull(target.getBlockedAt());
        assertNull(target.getBlockReason());
        assertNull(target.getBlockedBy());
        assertFalse(detail.blocked());
        verify(userRepository).save(target);
    }

    @Test
    void adminCannotBlockSelf() {
        UUID adminId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.blockUser(adminId, "reason", adminId)
        );

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void blockRequiresReason() {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.blockUser(targetId, "   ", adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(userRepository, never()).findByIdForUpdate(targetId);
    }

    @Test
    void blockRejectsReasonLongerThan512Characters() {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String longReason = "x".repeat(513);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.blockUser(targetId, longReason, adminId)
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(userRepository, never()).findByIdForUpdate(targetId);
    }

    @Test
    void adminCannotBlockAnotherAdmin() {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UserEntity target = user(targetId, "Other Admin", Role.ADMIN);

        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.blockUser(targetId, "reason", adminId)
        );

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void blockingAlreadyBlockedUserReturnsConflict() {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UserEntity target = user(targetId, "Target", Role.NETA);
        target.setBlocked(true);

        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.blockUser(targetId, "reason", adminId)
        );

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void userListReturnsBlockedSummaryField() {
        UserEntity blockedUser = user(UUID.randomUUID(), "Blocked User", Role.NETA);
        blockedUser.setBlocked(true);
        when(userRepository.findForAdmin(null, null, true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(blockedUser), PageRequest.of(0, 20), 1));

        AdminDtos.PageResponse<AdminDtos.AdminUserSummary> page =
                service.getUsers(null, null, true, PageRequest.of(0, 20));

        assertEquals(1, page.content().size());
        assertTrue(page.content().getFirst().blocked());
    }

    private UserEntity user(UUID id, String name, Role role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setDisplayName(name);
        user.setPhoneNumber("+919876543210");
        user.setRoleSet(Set.of(role));
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setPreferredLanguage("en-IN");
        return user;
    }
}
