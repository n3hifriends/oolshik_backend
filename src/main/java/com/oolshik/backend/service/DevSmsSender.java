package com.oolshik.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(DevSmsSender.class);
    @Override
    public void send(String phone, String message) { log.info("[DEV SMS] to {}: {}", phone, message); }
}
