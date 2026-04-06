package com.oolshik.backend.service;

public class OtpDeliveryException extends RuntimeException {

    private static final String MESSAGE_KEY = "errors.auth.otpDeliveryFailed";

    public OtpDeliveryException(String message) {
        super(message);
    }

    public OtpDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public String messageKey() {
        return MESSAGE_KEY;
    }
}
