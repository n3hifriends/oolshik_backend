package com.oolshik.backend.web;

import com.oolshik.backend.config.LocaleSupport;
import com.oolshik.backend.web.error.ConflictOperationException;
import com.oolshik.backend.web.error.ForbiddenOperationException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import java.util.Locale;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, String> BUSINESS_MESSAGE_KEYS = Map.ofEntries(
            Map.entry("Invalid credentials", "errors.auth.invalidCredentials"),
            Map.entry("Not a refresh token", "errors.auth.notRefreshToken"),
            Map.entry("Requester can't accept", "errors.requesterCannotAccept"),
            Map.entry("Request not open", "errors.requestNotOpen"),
            Map.entry("Only requester can authorize", "errors.onlyRequesterAuthorize"),
            Map.entry("Authorization not allowed", "errors.authorizationNotAllowed"),
            Map.entry("Only requester can reject", "errors.onlyRequesterReject"),
            Map.entry("reasonCode is required", "errors.reasonCodeRequired"),
            Map.entry("Reason is required when reasonCode is OTHER", "errors.reasonRequiredOther"),
            Map.entry("Request not pending authorization", "errors.requestNotPendingAuth"),
            Map.entry("Only requester can complete", "errors.onlyRequesterComplete"),
            Map.entry("Request already cancelled", "errors.requestAlreadyCancelled"),
            Map.entry("Only requester or helper can rate", "errors.onlyRequesterOrHelperRate"),
            Map.entry("Request not completed yet", "errors.requestNotCompleted"),
            Map.entry("Only requester can cancel", "errors.onlyRequesterCancel"),
            Map.entry("Request cannot be cancelled", "errors.requestCannotCancel"),
            Map.entry("Only assigned helper can release", "errors.onlyAssignedHelperRelease"),
            Map.entry("Request cannot be released", "errors.requestCannotRelease"),
            Map.entry("Only requester can reassign", "errors.onlyRequesterReassign"),
            Map.entry("Request not assigned", "errors.requestNotAssigned"),
            Map.entry("Request not accepted yet", "errors.requestNotAccepted"),
            Map.entry("Reassign not allowed yet", "errors.reassignNotAllowedYet"),
            Map.entry("Only requester can update offer", "errors.onlyRequesterUpdateOffer"),
            Map.entry("Offer can only be updated for OPEN and unassigned requests", "errors.offerUpdateNotAllowed"),
            Map.entry("offerAmount cannot be negative", "errors.offerAmountNegative"),
            Map.entry("Coordinates are missing.", "errors.locationUnavailable"),
            Map.entry("Authentication required", "errors.auth.required"),
            Map.entry("User not registered", "errors.auth.userNotRegistered"),
            Map.entry("Payment request not found", "errors.payment.notFound"),
            Map.entry("Active payment request not found", "errors.payment.activeNotFound"),
            Map.entry("Not your payment request", "errors.payment.notParticipant"),
            Map.entry("Only payer can perform this action", "errors.payment.onlyPayer")
    );

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

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
                        return localizeFieldError(fe);
                    }
                    return err.getDefaultMessage() != null
                            ? err.getDefaultMessage()
                            : message("errors.validationFailed", null, "Validation failed");
                })
                .orElse(message("errors.validationFailed", null, "Validation failed"));
        log.warn("[{}] 400 validation_error: {}", cid(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(cid(), "validation_error", msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        // Bad client input, not a server error
        String msg = localizeBusinessMessage(ex.getMessage(), "errors.invalidArgument");
        log.warn("[{}] 400 invalid_argument: {}", cid(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(cid(), "invalid_argument", msg));
    }

    /* ---------------------------
     *  403 – Forbidden
     * --------------------------- */
    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException ex) {
        String msg = localizeBusinessMessage(ex.getMessage(), "errors.forbidden");
        log.warn("[{}] 403 forbidden: {}", cid(), msg);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(cid(), "forbidden", msg));
    }

    /* ---------------------------
     *  404 – Not found
     * --------------------------- */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
        String msg = localizeBusinessMessage(ex.getMessage(), "errors.notFound");
        log.warn("[{}] 404 not_found: {}", cid(), msg);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(cid(), "not_found", msg));
    }

    /* ---------------------------
     *  409 – Conflict / Invalid state
     * --------------------------- */
    @ExceptionHandler(ConflictOperationException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictOperationException ex) {
        String msg = localizeBusinessMessage(ex.getMessage(), "errors.conflict");
        log.warn("[{}] 409 conflict: {}", cid(), msg);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(cid(), "conflict", msg));
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
        String localizedMessage = localizeBusinessMessage(message, "errors." + errorCode);

        if (status.is4xxClientError()) {
            log.warn("[{}] {} {}: {}", cid(), status.value(), errorCode, localizedMessage);
        } else {
            log.error("[{}] {} {}: {}", cid(), status.value(), errorCode, localizedMessage, ex);
        }

        return ResponseEntity.status(status)
                .body(new ApiError(cid(), errorCode, localizedMessage));
    }

    /* ---------------------------
     *  Fallback – real 500s only
     * --------------------------- */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex) {
        // Keep stacktrace in logs, short message to client
        log.error("[{}] 500 internal_error", cid(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        cid(),
                        "internal_error",
                        message("errors.internal", null, "Unexpected error")
                ));
    }


     @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("error", "invalid_request");
        responseBody.put(
                "message",
                message(
                        "errors.methodArgumentTypeMismatch",
                        new Object[]{
                                exception.getName(),
                                exception.getValue(),
                                exception.getRequiredType() != null
                                        ? exception.getRequiredType().getSimpleName()
                                        : "required type"
                        },
                        String.format(
                                "Parameter '%s' has invalid value '%s'. Expected %s.",
                                exception.getName(),
                                exception.getValue(),
                                exception.getRequiredType() != null
                                        ? exception.getRequiredType().getSimpleName()
                                        : "required type"
                        )
                ));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
    }

    private String localizeFieldError(FieldError fe) {
        String fallback = fe.getDefaultMessage() == null
                ? message("errors.validationFailed", null, "Validation failed")
                : fe.getDefaultMessage();
        String localized = message("validation." + fe.getCode(), fe.getArguments(), fallback);
        return fe.getField() + ": " + localized;
    }

    private String localizeBusinessMessage(String rawMessage, String defaultKey) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return message(defaultKey, null, "Unexpected error");
        }
        if (rawMessage.startsWith("errors.")) {
            return message(rawMessage, null, rawMessage);
        }
        String key = BUSINESS_MESSAGE_KEYS.get(rawMessage);
        if (key != null) {
            return message(key, null, rawMessage);
        }
        return rawMessage;
    }

    private String message(String key, Object[] args, String fallback) {
        Locale locale = LocaleSupport.normalizeLocale(LocaleContextHolder.getLocale());
        return messageSource.getMessage(key, args, fallback, locale);
    }
}
