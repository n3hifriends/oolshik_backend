package com.oolshik.backend.service;

import com.oolshik.backend.config.LocalizationConfig;
import com.oolshik.backend.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerLocalizationTest {

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void illegalArgumentMessageIsLocalizedToMarathi() {
        MessageSource messageSource = new LocalizationConfig().messageSource();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("mr-IN"));

        ResponseEntity<?> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Invalid credentials")
        );

        String body = String.valueOf(response.getBody());
        assertTrue(body.contains("लॉगिन तपशील"));
    }

    @Test
    void responseStatusMessageKeyIsLocalizedToMarathi() {
        MessageSource messageSource = new LocalizationConfig().messageSource();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("mr-IN"));

        ResponseEntity<?> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "errors.report.taskOrTargetRequired")
        );

        String body = String.valueOf(response.getBody());
        assertTrue(body.contains("यापैकी एक"));
    }
}

