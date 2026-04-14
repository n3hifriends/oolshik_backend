package com.oolshik.backend.service;

import com.oolshik.backend.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdTokenVerifierServiceTest {

    @Test
    void verifyFailsWithClientErrorWhenGoogleIsNotConfigured() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.getGoogle().setEnabled(true);
        GoogleIdTokenVerifierService service = new GoogleIdTokenVerifierService(authProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.verify("google-id-token")
        );

        assertEquals("errors.auth.googleNotConfigured", ex.getMessage());
    }
}
