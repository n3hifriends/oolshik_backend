package com.oolshik.notificationworker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public class NotificationWorkerProperties {

    private String expoEndpoint = "https://exp.host/--/api/v2/push/send";
    private int maxSendAttempts = 3;
    private int coalesceWindowSeconds = 10;
    private int expoBatchSize = 100;

    public String getExpoEndpoint() {
        return expoEndpoint;
    }

    public void setExpoEndpoint(String expoEndpoint) {
        this.expoEndpoint = expoEndpoint;
    }

    public int getMaxSendAttempts() {
        return maxSendAttempts;
    }

    public void setMaxSendAttempts(int maxSendAttempts) {
        this.maxSendAttempts = maxSendAttempts;
    }

    public int getCoalesceWindowSeconds() {
        return coalesceWindowSeconds;
    }

    public void setCoalesceWindowSeconds(int coalesceWindowSeconds) {
        this.coalesceWindowSeconds = coalesceWindowSeconds;
    }

    public int getExpoBatchSize() {
        return expoBatchSize;
    }

    public void setExpoBatchSize(int expoBatchSize) {
        this.expoBatchSize = expoBatchSize;
    }
}
