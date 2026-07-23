package com.oolshik.backend.web.error;

public class AccountDeletedException extends RuntimeException {
    public AccountDeletedException() {
        super("This account has been deleted.");
    }
}
