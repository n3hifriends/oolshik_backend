package com.oolshik.notificationworker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicProperties {

    private String notificationEvents;

    public String getNotificationEvents() {
        return notificationEvents;
    }

    public void setNotificationEvents(String notificationEvents) {
        this.notificationEvents = notificationEvents;
    }
}
