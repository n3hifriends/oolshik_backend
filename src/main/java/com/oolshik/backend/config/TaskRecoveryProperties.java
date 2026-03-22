package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.task-recovery")
public class TaskRecoveryProperties {

    private long acceptToStartSlaSeconds = 420;
    private long assignmentTtlSeconds = 2700;
    private long authTtlSeconds = 120;
    private long completionConfirmationTtlHours = 12;
    private double reminder50Percent = 0.5d;
    private double reminder80Percent = 0.8d;
    private int maxReassign = 2;
    private int schedulerBatchSize = 100;
    private long schedulerDelayMs = 60000;

    public long getAcceptToStartSlaSeconds() { return acceptToStartSlaSeconds; }
    public void setAcceptToStartSlaSeconds(long acceptToStartSlaSeconds) {
        this.acceptToStartSlaSeconds = acceptToStartSlaSeconds;
    }
    public long getAssignmentTtlSeconds() { return assignmentTtlSeconds; }
    public void setAssignmentTtlSeconds(long assignmentTtlSeconds) {
        this.assignmentTtlSeconds = assignmentTtlSeconds;
    }
    public long getAuthTtlSeconds() { return authTtlSeconds; }
    public void setAuthTtlSeconds(long authTtlSeconds) { this.authTtlSeconds = authTtlSeconds; }
    public long getCompletionConfirmationTtlHours() { return completionConfirmationTtlHours; }
    public void setCompletionConfirmationTtlHours(long completionConfirmationTtlHours) {
        this.completionConfirmationTtlHours = completionConfirmationTtlHours;
    }
    public double getReminder50Percent() { return reminder50Percent; }
    public void setReminder50Percent(double reminder50Percent) { this.reminder50Percent = reminder50Percent; }
    public double getReminder80Percent() { return reminder80Percent; }
    public void setReminder80Percent(double reminder80Percent) { this.reminder80Percent = reminder80Percent; }
    public int getMaxReassign() { return maxReassign; }
    public void setMaxReassign(int maxReassign) { this.maxReassign = maxReassign; }
    public int getSchedulerBatchSize() { return schedulerBatchSize; }
    public void setSchedulerBatchSize(int schedulerBatchSize) { this.schedulerBatchSize = schedulerBatchSize; }
    public long getSchedulerDelayMs() { return schedulerDelayMs; }
    public void setSchedulerDelayMs(long schedulerDelayMs) { this.schedulerDelayMs = schedulerDelayMs; }
}
