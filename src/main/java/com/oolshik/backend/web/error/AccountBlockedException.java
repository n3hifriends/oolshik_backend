package com.oolshik.backend.web.error;

public class AccountBlockedException extends RuntimeException {
    public AccountBlockedException() {
        super("Your account has been blocked. Contact support.");
    }
}
