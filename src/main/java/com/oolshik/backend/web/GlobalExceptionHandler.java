package com.oolshik.backend.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String cid = MDC.get("cid");
        Map<String, Object> body = new HashMap<>();
        body.put("error", "validation_error");
        body.put("cid", cid);
        body.put("message", ex.getMessage());
        log.warn("[{}] Validation failed: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAny(Exception ex) {
        String cid = MDC.get("cid");
        Map<String, Object> body = new HashMap<>();
        body.put("error", "internal_error");
        body.put("cid", cid);
        body.put("message", ex.getMessage());
        log.error("[{}] Unhandled exception", cid, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
