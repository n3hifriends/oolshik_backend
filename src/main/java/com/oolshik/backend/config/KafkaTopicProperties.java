package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicProperties {

    private String sttJobs;
    private String sttResults;
    private String sttDlq;

    public String getSttJobs() { return sttJobs; }
    public void setSttJobs(String sttJobs) { this.sttJobs = sttJobs; }
    public String getSttResults() { return sttResults; }
    public void setSttResults(String sttResults) { this.sttResults = sttResults; }
    public String getSttDlq() { return sttDlq; }
    public void setSttDlq(String sttDlq) { this.sttDlq = sttDlq; }
}
