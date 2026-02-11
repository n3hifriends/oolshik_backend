package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private int activeWindowMinutes = 15;
    private int outboxPublishIntervalMs = 2000;
    private int outboxBatchSize = 50;
    private int outboxMaxAttempts = 8;

    public int getActiveWindowMinutes() {
        return activeWindowMinutes;
    }

    public void setActiveWindowMinutes(int activeWindowMinutes) {
        this.activeWindowMinutes = activeWindowMinutes;
    }

    public int getOutboxPublishIntervalMs() {
        return outboxPublishIntervalMs;
    }

    public void setOutboxPublishIntervalMs(int outboxPublishIntervalMs) {
        this.outboxPublishIntervalMs = outboxPublishIntervalMs;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public int getOutboxMaxAttempts() {
        return outboxMaxAttempts;
    }

    public void setOutboxMaxAttempts(int outboxMaxAttempts) {
        this.outboxMaxAttempts = outboxMaxAttempts;
    }
}
