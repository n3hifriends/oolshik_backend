package com.oolshik.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.notification.AssignmentChange;
import com.oolshik.backend.notification.NotificationEventContext;
import com.oolshik.backend.notification.NotificationEventPayload;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.notification.NotificationOutboxStatus;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HelpRequestNotificationServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void enqueueTaskEventWritesOutbox() throws Exception {
        NotificationOutboxRepository repo = mock(NotificationOutboxRepository.class);
        HelpRequestNotificationService service = new HelpRequestNotificationService(repo, new ObjectMapper());

        HelpRequestEntity task = new HelpRequestEntity();
        UUID taskId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        task.setId(taskId);
        task.setRequesterId(requesterId);
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(72.0, 19.0));
        task.setLocation(point);

        NotificationEventContext context = new NotificationEventContext();
        context.setActorUserId(requesterId);
        context.setPreviousStatus("OPEN");
        context.setNewStatus("CANCELLED");
        context.setAssignmentChange(AssignmentChange.UNASSIGNED);
        context.setOccurredAt(OffsetDateTime.now());

        service.enqueueTaskEvent(NotificationEventType.TASK_CANCELLED, task, context);

        ArgumentCaptor<NotificationOutboxEntity> captor = ArgumentCaptor.forClass(NotificationOutboxEntity.class);
        verify(repo).save(captor.capture());
        NotificationOutboxEntity outbox = captor.getValue();
        assertEquals(NotificationEventType.TASK_CANCELLED.name(), outbox.getEventType());
        assertEquals(NotificationOutboxStatus.PENDING.name(), outbox.getStatus());
        assertNotNull(outbox.getPayloadJson());

        NotificationEventPayload payload = new ObjectMapper().readValue(outbox.getPayloadJson(), NotificationEventPayload.class);
        assertEquals(taskId, payload.getTaskId());
        assertEquals(requesterId, payload.getRequesterUserId());
        assertEquals(NotificationEventType.TASK_CANCELLED.name(), payload.getEventType());
    }
}
