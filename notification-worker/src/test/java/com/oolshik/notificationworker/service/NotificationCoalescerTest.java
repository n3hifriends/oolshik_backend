package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.config.NotificationWorkerProperties;
import com.oolshik.notificationworker.model.NotificationEventPayload;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationCoalescerTest {

    @Test
    void higherPriorityEventWins() {
        NotificationWorkerProperties props = new NotificationWorkerProperties();
        props.setCoalesceWindowSeconds(0);
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        NotificationCoalescer coalescer = new NotificationCoalescer(dispatcher, props);

        UUID taskId = UUID.randomUUID();
        NotificationEventPayload low = new NotificationEventPayload();
        low.setTaskId(taskId);
        low.setEventType("TASK_CREATED");

        NotificationEventPayload high = new NotificationEventPayload();
        high.setTaskId(taskId);
        high.setEventType("TASK_CANCELLED");

        coalescer.enqueue(low);
        coalescer.enqueue(high);
        coalescer.flushReady();

        assertEquals("TASK_CANCELLED", dispatcher.lastEventType);
    }

    private static class CapturingDispatcher extends NotificationDispatcher {
        private String lastEventType;

        CapturingDispatcher() {
            super(null, null, null, null, null, null, new NotificationWorkerProperties());
        }

        @Override
        public void dispatch(NotificationEventPayload payload) {
            lastEventType = payload.getEventType();
        }
    }
}
