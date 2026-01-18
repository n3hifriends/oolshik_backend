package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.task-recovery")
public class TaskRecoveryProperties {

    private long acceptToStartSlaSeconds = 420;
    private long assignmentTtlSeconds = 2700;
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
    public int getMaxReassign() { return maxReassign; }
    public void setMaxReassign(int maxReassign) { this.maxReassign = maxReassign; }
    public int getSchedulerBatchSize() { return schedulerBatchSize; }
    public void setSchedulerBatchSize(int schedulerBatchSize) { this.schedulerBatchSize = schedulerBatchSize; }
    public long getSchedulerDelayMs() { return schedulerDelayMs; }
    public void setSchedulerDelayMs(long schedulerDelayMs) { this.schedulerDelayMs = schedulerDelayMs; }
}
