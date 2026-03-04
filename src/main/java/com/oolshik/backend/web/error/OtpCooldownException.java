package com.oolshik.backend.web.error;

public class OtpCooldownException extends RuntimeException {

    private static final String MESSAGE_KEY = "errors.auth.otpCooldown";
    private final long waitSeconds;

    public OtpCooldownException(long waitSeconds) {
        super(MESSAGE_KEY);
        this.waitSeconds = waitSeconds;
    }

    public long waitSeconds() {
        return waitSeconds;
    }
}
