package com.oolshik.backend.service;

import com.oolshik.backend.util.MaskingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "dev", matchIfMissing = true)
public class DevOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(DevOtpProvider.class);

    @Override
    public String providerId() {
        return "dev";
    }

    @Override
    public void sendOtp(String phone, String message) {
        log.info("OTP dispatched via dev provider to {}", MaskingUtils.maskPhone(phone));
    }
}
