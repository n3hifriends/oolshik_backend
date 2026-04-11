package com.oolshik.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.oolshik.backend.config.AuthProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoogleIdTokenVerifierService {

    private static final Set<String> VALID_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final GoogleIdTokenVerifier verifier;
    private final Set<String> allowedClientIds;
    private final AuthProperties authProperties;

    public GoogleIdTokenVerifierService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.allowedClientIds = authProperties.getGoogle().getAllowedClientIds().stream()
                .map(value -> value == null ? null : value.trim())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                .setAudience(this.allowedClientIds.isEmpty() ? null : List.copyOf(this.allowedClientIds))
                .build();
    }

    public GoogleIdentityClaims verify(String idToken) {
        if (!authProperties.getGoogle().isEnabled()) {
            throw new IllegalArgumentException("errors.auth.googleDisabled");
        }
        if (allowedClientIds.isEmpty()) {
            throw new IllegalArgumentException("errors.auth.googleNotConfigured");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("errors.auth.googleIdTokenInvalid");
        }
        try {
            GoogleIdToken verified = verifier.verify(idToken);
            if (verified == null) {
                throw new IllegalArgumentException("errors.auth.googleIdTokenInvalid");
            }
            GoogleIdToken.Payload payload = verified.getPayload();
            if (!VALID_ISSUERS.contains(payload.getIssuer())) {
                throw new IllegalArgumentException("errors.auth.googleIdTokenInvalid");
            }
            String subject = payload.getSubject();
            String email = normalizeEmail(payload.getEmail());
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            if (subject == null || subject.isBlank() || email == null || !emailVerified) {
                throw new IllegalArgumentException("errors.auth.googleEmailNotVerified");
            }
            return new GoogleIdentityClaims(
                    subject,
                    email,
                    true,
                    asString(payload.get("name"))
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalArgumentException("errors.auth.googleIdTokenInvalid");
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String asString(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    public record GoogleIdentityClaims(
            String subject,
            String email,
            boolean emailVerified,
            String displayName
    ) {}
}
