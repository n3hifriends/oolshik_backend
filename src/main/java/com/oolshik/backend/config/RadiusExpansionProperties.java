package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.radius-expansion")
public class RadiusExpansionProperties {

    private List<Integer> scheduleMeters = new ArrayList<>(List.of(1000, 2000, 3000, 5000));
    private List<Integer> escalationDelaysSeconds = new ArrayList<>(List.of(0, 120, 300, 600));
    private int maxRadiusMeters = 5000;
    private int locationFreshnessHours = 24;
    private int jobIntervalSeconds = 30;
    private int batchSize = 100;

    public List<Integer> getScheduleMeters() { return scheduleMeters; }
    public void setScheduleMeters(List<Integer> scheduleMeters) { this.scheduleMeters = scheduleMeters; }
    public List<Integer> getEscalationDelaysSeconds() { return escalationDelaysSeconds; }
    public void setEscalationDelaysSeconds(List<Integer> escalationDelaysSeconds) {
        this.escalationDelaysSeconds = escalationDelaysSeconds;
    }
    public int getMaxRadiusMeters() { return maxRadiusMeters; }
    public void setMaxRadiusMeters(int maxRadiusMeters) { this.maxRadiusMeters = maxRadiusMeters; }
    public int getLocationFreshnessHours() { return locationFreshnessHours; }
    public void setLocationFreshnessHours(int locationFreshnessHours) { this.locationFreshnessHours = locationFreshnessHours; }
    public int getJobIntervalSeconds() { return jobIntervalSeconds; }
    public void setJobIntervalSeconds(int jobIntervalSeconds) { this.jobIntervalSeconds = jobIntervalSeconds; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
