package com.oolshik.backend.security;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUserPrincipal(
        String identityProvider,
        String providerUserId,
        String phone,
        String email,
        UUID userId
) implements Principal {

    @Override
    public String getName() {
        if (userId != null) {
            return userId.toString();
        }
        if (phone != null && !phone.isBlank()) {
            return phone;
        }
        if (providerUserId != null && !providerUserId.isBlank()) {
            return providerUserId;
        }
        return email != null ? email : "";
    }

    public boolean isFirebaseIdentity() {
        return "firebase".equalsIgnoreCase(identityProvider);
    }
}
