package com.oolshik.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "none")
public class NoopOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(NoopOtpProvider.class);

    @Override
    public String providerId() {
        return "none";
    }

    @Override
    public void sendOtp(String phone, String message) {
        log.warn("OTP send ignored — app.otp.provider=none. Phone auth is disabled.");
    }
}
