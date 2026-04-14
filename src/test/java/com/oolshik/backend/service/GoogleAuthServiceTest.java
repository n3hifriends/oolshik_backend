package com.oolshik.backend.service;

import com.oolshik.backend.config.AuthProperties;
import com.oolshik.backend.entity.FederatedIdentityEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.FederatedIdentityRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.web.dto.AuthDtos.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private GoogleIdTokenVerifierService googleIdTokenVerifierService;
    @Mock
    private FederatedIdentityRepository federatedIdentityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    private GoogleAuthService service;
    private AuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.getGoogle().setEnabled(true);
        authProperties.getGoogle().setRequirePhone(true);
        authProperties.getGoogle().setAutoLinkByEmail(false);
        service = new GoogleAuthService(
                googleIdTokenVerifierService,
                federatedIdentityRepository,
                userRepository,
                jwtService,
                authProperties
        );
    }

    @Test
    void authenticateRequiresPhoneForNewGoogleSignup() {
        GoogleIdTokenVerifierService.GoogleIdentityClaims claims =
                new GoogleIdTokenVerifierService.GoogleIdentityClaims(
                        "google-subject",
                        "user@example.com",
                        true,
                        "User"
                );
        when(googleIdTokenVerifierService.verify("google-id-token")).thenReturn(claims);
        when(federatedIdentityRepository.findByProviderAndProviderSubject("google", "google-subject"))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.authenticate("google-id-token", null)
        );

        assertEquals("errors.auth.googlePhoneRequired", ex.getMessage());
    }

    @Test
    void authenticateRepairsExistingGoogleUserWithoutPhone() {
        UUID userId = UUID.randomUUID();
        GoogleIdTokenVerifierService.GoogleIdentityClaims claims =
                new GoogleIdTokenVerifierService.GoogleIdentityClaims(
                        "google-subject",
                        "user@example.com",
                        true,
                        "User"
                );
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUserId(userId);
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setDisplayName("User");

        when(googleIdTokenVerifierService.verify("google-id-token")).thenReturn(claims);
        when(federatedIdentityRepository.findByProviderAndProviderSubject("google", "google-subject"))
                .thenReturn(Optional.of(identity));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("+919876543210")).thenReturn(Optional.empty());
        when(federatedIdentityRepository.save(any(FederatedIdentityEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(userId, "+919876543210")).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userId)).thenReturn("refresh-token");

        TokenResponse tokens = service.authenticate("google-id-token", "+919876543210");

        assertEquals("+919876543210", user.getPhoneNumber());
        assertEquals("access-token", tokens.accessToken());
        assertEquals("refresh-token", tokens.refreshToken());
    }
}
