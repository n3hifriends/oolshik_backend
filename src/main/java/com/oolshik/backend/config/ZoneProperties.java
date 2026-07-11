package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.zone")
public class ZoneProperties {

    private boolean bypassWhenNoZonesActive = true;

    public boolean isBypassWhenNoZonesActive() { return bypassWhenNoZonesActive; }
    public void setBypassWhenNoZonesActive(boolean bypassWhenNoZonesActive) {
        this.bypassWhenNoZonesActive = bypassWhenNoZonesActive;
    }
}
