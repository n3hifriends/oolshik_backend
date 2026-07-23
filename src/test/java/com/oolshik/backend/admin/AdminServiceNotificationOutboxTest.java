package com.oolshik.backend.admin;

import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceNotificationOutboxTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void acknowledgeDeadMessageRecordsAdminAndResolution() {
        UUID id = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        NotificationOutboxEntity outbox = outbox(id, "DEAD");

        when(notificationOutboxRepository.findById(id)).thenReturn(Optional.of(outbox));
        when(notificationOutboxRepository.acknowledgeDead(eq(id), eq(adminId), eq("Investigated"), any()))
                .thenAnswer(invocation -> {
                    outbox.setAcknowledgedBy(adminId);
                    outbox.setAcknowledgedAt(invocation.getArgument(3));
                    outbox.setResolutionNote("Investigated");
                    return 1;
                });

        AdminDtos.AdminNotificationRow result =
                adminService.acknowledgeNotification(id, "Investigated", adminId);

        assertEquals(adminId, result.acknowledgedBy());
        assertEquals("Investigated", result.resolutionNote());
    }

    @Test
    void acknowledgeRejectsNonDeadMessage() {
        UUID id = UUID.randomUUID();
        when(notificationOutboxRepository.findById(id)).thenReturn(Optional.of(outbox(id, "FAILED")));

        assertThrows(
                ResponseStatusException.class,
                () -> adminService.acknowledgeNotification(id, null, UUID.randomUUID())
        );
    }

    @Test
    void requeueResetsEligibleFailureThroughConditionalUpdate() {
        UUID id = UUID.randomUUID();
        NotificationOutboxEntity outbox = outbox(id, "DEAD");
        when(notificationOutboxRepository.existsById(id)).thenReturn(true);
        when(notificationOutboxRepository.requeueFailure(eq(id), any())).thenAnswer(invocation -> {
            outbox.setStatus("PENDING");
            outbox.setAttemptCount(0);
            outbox.setLastError(null);
            return 1;
        });
        when(notificationOutboxRepository.findById(id)).thenReturn(Optional.of(outbox));

        AdminDtos.AdminNotificationRow result = adminService.requeueNotification(id);

        assertEquals("PENDING", result.status());
        assertEquals(0, result.attempts());
        verify(notificationOutboxRepository).requeueFailure(eq(id), any(OffsetDateTime.class));
    }

    @Test
    void requeueRejectsPublishedMessage() {
        UUID id = UUID.randomUUID();
        when(notificationOutboxRepository.existsById(id)).thenReturn(true);
        when(notificationOutboxRepository.requeueFailure(eq(id), any())).thenReturn(0);

        assertThrows(ResponseStatusException.class, () -> adminService.requeueNotification(id));
    }

    private NotificationOutboxEntity outbox(UUID id, String status) {
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setId(id);
        outbox.setEventType("TEST_EVENT");
        outbox.setAggregateId(UUID.randomUUID());
        outbox.setPayloadJson("{}");
        outbox.setStatus(status);
        outbox.setAttemptCount(8);
        outbox.setLastError("Delivery failed");
        outbox.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        outbox.setUpdatedAt(OffsetDateTime.now());
        return outbox;
    }
}
