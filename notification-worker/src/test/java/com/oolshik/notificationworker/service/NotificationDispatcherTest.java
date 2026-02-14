package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.config.NotificationWorkerProperties;
import com.oolshik.notificationworker.entity.NotificationDeliveryLogEntity;
import com.oolshik.notificationworker.entity.UserDeviceEntity;
import com.oolshik.notificationworker.model.ExpoPushResponse;
import com.oolshik.notificationworker.model.NotificationEventPayload;
import com.oolshik.notificationworker.repo.HelpRequestCandidateRepository;
import com.oolshik.notificationworker.repo.NotificationDeliveryLogRepository;
import com.oolshik.notificationworker.repo.UserDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private RecipientResolver recipientResolver;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private NotificationDeliveryLogRepository deliveryLogRepository;
    @Mock
    private HelpRequestCandidateRepository candidateRepository;
    @Mock
    private NotificationTemplateService templateService;
    @Mock
    private ExpoPushClient expoPushClient;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        NotificationWorkerProperties properties = new NotificationWorkerProperties();
        properties.setExpoBatchSize(100);
        dispatcher = new NotificationDispatcher(
                recipientResolver,
                userDeviceRepository,
                deliveryLogRepository,
                candidateRepository,
                templateService,
                expoPushClient,
                properties
        );
    }

    @Test
    void idempotencySkipsSent() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationEventPayload payload = new NotificationEventPayload();
        payload.setEventId(UUID.randomUUID());
        payload.setEventType("TASK_CANCELLED");
        payload.setTaskId(taskId);

        when(recipientResolver.resolve(payload)).thenReturn(List.of(userId));
        NotificationDeliveryLogEntity existing = new NotificationDeliveryLogEntity();
        existing.setStatus("SENT");
        when(deliveryLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existing));
        when(userDeviceRepository.findPreferredLocalesByUserIds(anyList())).thenReturn(List.of());

        dispatcher.dispatch(payload);

        verify(expoPushClient, never()).send(anyList());
        verify(userDeviceRepository, never()).findActiveByUserIds(anyList());
    }

    @Test
    void invalidTokenDeactivatesDevice() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationEventPayload payload = new NotificationEventPayload();
        payload.setEventId(UUID.randomUUID());
        payload.setEventType("TASK_CANCELLED");
        payload.setTaskId(taskId);

        when(recipientResolver.resolve(payload)).thenReturn(List.of(userId));
        when(deliveryLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(userDeviceRepository.findPreferredLocalesByUserIds(anyList())).thenReturn(List.of(new UserDeviceRepository.UserLocaleRow() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public String getPreferredLanguage() {
                return "mr-IN";
            }
        }));

        UserDeviceEntity device = new UserDeviceEntity();
        device.setUserId(userId);
        device.setToken("ExponentPushToken[abc]");
        when(userDeviceRepository.findActiveByUserIds(anyList())).thenReturn(List.of(device));

        when(templateService.templateFor(eq("TASK_CANCELLED"), any(), eq("mr-IN")))
                .thenReturn(new NotificationTemplateService.NotificationTemplate("t", "b"));

        ExpoPushResponse.ExpoPushTicket ticket = new ExpoPushResponse.ExpoPushTicket();
        ticket.setStatus("error");
        ticket.setMessage("DeviceNotRegistered");
        ticket.setDetails(Map.of("error", "DeviceNotRegistered"));
        ExpoPushResponse response = new ExpoPushResponse();
        response.setData(List.of(ticket));

        when(expoPushClient.send(anyList())).thenReturn(response);

        dispatcher.dispatch(payload);

        String expectedHash = HashUtil.sha256(device.getToken());
        verify(userDeviceRepository).deactivateByTokenHash(eq(expectedHash));
        verify(deliveryLogRepository).updateStatus(any(), eq("FAILED"), any(), any(OffsetDateTime.class));
        verify(templateService).templateFor(eq("TASK_CANCELLED"), any(), eq("mr-IN"));
    }
}
