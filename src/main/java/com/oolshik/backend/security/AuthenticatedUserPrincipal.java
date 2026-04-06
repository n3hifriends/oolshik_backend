package com.oolshik.backend.security;

import java.security.Principal;

public record AuthenticatedUserPrincipal(
        String identityProvider,
        String providerUserId,
        String phone,
        String email
) implements Principal {

    @Override
    public String getName() {
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
