package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@Component
@ConfigurationProperties(prefix = "app.admin.notification")
@Validated
public class AdminNotificationProperties {

    @Min(1)
    private long schedulerIntervalMs = 5000;
    @Min(1)
    private int batchSize = 100;
    @Min(1)
    private int maxRecipients = 50000;
    @Min(1)
    private int staleProcessingTimeoutMinutes = 10;

    /** Controls which push provider is active: FCM (default) or EXPO. */
    @Pattern(regexp = "FCM|EXPO", message = "pushProvider must be FCM or EXPO")
    private String pushProvider = "FCM";

    private Sms sms = new Sms();

    public static class Sms {
        private boolean enabled = false;
        private String msg91ApiKey;
        private String msg91SenderId;
        private String msg91BaseUrl = "https://api.msg91.com/api/sendhttp.php";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMsg91ApiKey() { return msg91ApiKey; }
        public void setMsg91ApiKey(String msg91ApiKey) { this.msg91ApiKey = msg91ApiKey; }
        public String getMsg91SenderId() { return msg91SenderId; }
        public void setMsg91SenderId(String msg91SenderId) { this.msg91SenderId = msg91SenderId; }
        public String getMsg91BaseUrl() { return msg91BaseUrl; }
        public void setMsg91BaseUrl(String msg91BaseUrl) { this.msg91BaseUrl = msg91BaseUrl; }
    }

    public long getSchedulerIntervalMs() { return schedulerIntervalMs; }
    public void setSchedulerIntervalMs(long schedulerIntervalMs) { this.schedulerIntervalMs = schedulerIntervalMs; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxRecipients() { return maxRecipients; }
    public void setMaxRecipients(int maxRecipients) { this.maxRecipients = maxRecipients; }
    public int getStaleProcessingTimeoutMinutes() { return staleProcessingTimeoutMinutes; }
    public void setStaleProcessingTimeoutMinutes(int staleProcessingTimeoutMinutes) { this.staleProcessingTimeoutMinutes = staleProcessingTimeoutMinutes; }
    public String getPushProvider() { return pushProvider; }
    public void setPushProvider(String pushProvider) { this.pushProvider = pushProvider; }
    public Sms getSms() { return sms; }
    public void setSms(Sms sms) { this.sms = sms; }
}
