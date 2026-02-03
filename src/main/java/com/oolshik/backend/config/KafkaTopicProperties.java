package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicProperties {

    private String sttJobs;
    private String sttResults;
    private String sttDlq;
    private String notificationEvents;

    public String getSttJobs() { return sttJobs; }
    public void setSttJobs(String sttJobs) { this.sttJobs = sttJobs; }
    public String getSttResults() { return sttResults; }
    public void setSttResults(String sttResults) { this.sttResults = sttResults; }
    public String getSttDlq() { return sttDlq; }
    public void setSttDlq(String sttDlq) { this.sttDlq = sttDlq; }
    public String getNotificationEvents() { return notificationEvents; }
    public void setNotificationEvents(String notificationEvents) { this.notificationEvents = notificationEvents; }
}
