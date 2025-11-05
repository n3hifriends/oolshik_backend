package com.oolshik.backend.web;

import com.oolshik.backend.web.error.ConflictOperationException;
import com.oolshik.backend.web.error.ForbiddenOperationException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private record ApiError(String cid, String error, String message) {
    }

    private String cid() {
        return MDC.get("cid");
    }

    /* ---------------------------
     *  400 – Validation / Bad input
     * --------------------------- */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> {
                    if (err instanceof FieldError fe) {
                        return fe.getField() + ": " + fe.getDefaultMessage();
                    }
                    return err.getDefaultMessage();
                })
                .orElse("Validation failed");
        log.warn("[{}] 400 validation_error: {}", cid(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(cid(), "validation_error", msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        // Bad client input, not a server error
        log.warn("[{}] 400 invalid_argument: {}", cid(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(cid(), "invalid_argument", ex.getMessage()));
    }

    /* ---------------------------
     *  403 – Forbidden
     * --------------------------- */
    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException ex) {
        log.warn("[{}] 403 forbidden: {}", cid(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(cid(), "forbidden", ex.getMessage()));
    }

    /* ---------------------------
     *  404 – Not found
     * --------------------------- */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
        log.warn("[{}] 404 not_found: {}", cid(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(cid(), "not_found", ex.getMessage()));
    }

    /* ---------------------------
     *  409 – Conflict / Invalid state
     * --------------------------- */
    @ExceptionHandler(ConflictOperationException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictOperationException ex) {
        log.warn("[{}] 409 conflict: {}", cid(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(cid(), "conflict", ex.getMessage()));
    }

    /* ---------------------------
     *  Pass-through for ResponseStatus* exceptions
     * --------------------------- */
    @ExceptionHandler({ResponseStatusException.class, ErrorResponseException.class})
    public ResponseEntity<ApiError> handleResponseStatus(Exception ex) {
        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = (rse.getReason() != null ? rse.getReason() : rse.getMessage());
        } else {
            ErrorResponseException ere = (ErrorResponseException) ex;
            status = HttpStatus.valueOf(ere.getStatusCode().value());
            message = ere.getBody() != null && ere.getBody().getDetail() != null
                    ? ere.getBody().getDetail()
                    : ere.getMessage();
        }

        String errorCode = switch (status) {
            case BAD_REQUEST -> "bad_request";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "not_found";
            case CONFLICT -> "conflict";
            default -> "error";
        };

        if (status.is4xxClientError()) {
            log.warn("[{}] {} {}: {}", cid(), status.value(), errorCode, message);
        } else {
            log.error("[{}] {} {}: {}", cid(), status.value(), errorCode, message, ex);
        }

        return ResponseEntity.status(status)
                .body(new ApiError(cid(), errorCode, message));
    }

    /* ---------------------------
     *  Fallback – real 500s only
     * --------------------------- */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex) {
        // Keep stacktrace in logs, short message to client
        log.error("[{}] 500 internal_error", cid(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(cid(), "internal_error", "Unexpected error"));
    }


     @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("error", "invalid_request");
        responseBody.put(
                "message",
                String.format(
                        "Parameter '%s' has invalid value '%s'. Expected %s.",
                        exception.getName(),
                        exception.getValue(),
                        exception.getRequiredType() != null
                                ? exception.getRequiredType().getSimpleName()
                                : "required type"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
    }
}