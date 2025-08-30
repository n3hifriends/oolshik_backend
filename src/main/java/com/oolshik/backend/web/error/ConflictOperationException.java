package com.oolshik.backend.web.error;

public class ConflictOperationException extends RuntimeException {
    public ConflictOperationException(String message) {
        super(message);
    }
}