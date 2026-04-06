package com.oolshik.backend.service;

@Deprecated(forRemoval = false)
public interface SmsSender extends OtpProvider {

    void send(String phone, String message);

    @Override
    default String providerId() {
        return "legacy-sms";
    }

    @Override
    default void sendOtp(String phone, String message) {
        send(phone, message);
    }
}
