package com.oolshik.backend.service;

import com.oolshik.backend.config.AuthProperties;
import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.FederatedIdentityEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.FederatedIdentityRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.util.PhoneUtil;
import com.oolshik.backend.web.dto.AuthDtos.TokenResponse;
import com.oolshik.backend.web.error.ConflictOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;

@Service
public class GoogleAuthService {

    private static final String GOOGLE_PROVIDER = "google";

    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthProperties authProperties;

    public GoogleAuthService(
            GoogleIdTokenVerifierService googleIdTokenVerifierService,
            FederatedIdentityRepository federatedIdentityRepository,
            UserRepository userRepository,
            JwtService jwtService,
            AuthProperties authProperties
    ) {
        this.googleIdTokenVerifierService = googleIdTokenVerifierService;
        this.federatedIdentityRepository = federatedIdentityRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
    }

    @Transactional
    public TokenResponse authenticate(String idToken, String phoneHint) {
        if (!authProperties.getGoogle().isEnabled()) {
            throw new IllegalArgumentException("errors.auth.googleDisabled");
        }
        GoogleIdTokenVerifierService.GoogleIdentityClaims claims = googleIdTokenVerifierService.verify(idToken);

        UserEntity user = federatedIdentityRepository
                .findByProviderAndProviderSubject(GOOGLE_PROVIDER, claims.subject())
                .map(identity -> attachExisting(identity, claims, phoneHint))
                .orElseGet(() -> createLinkOrReject(claims, phoneHint));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getPhoneNumber());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        return new TokenResponse(accessToken, refreshToken);
    }

    private UserEntity attachExisting(
            FederatedIdentityEntity identity,
            GoogleIdTokenVerifierService.GoogleIdentityClaims claims,
            String phoneHint
    ) {
        UserEntity user = userRepository.findById(identity.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("errors.auth.userNotRegistered"));
        maybeAssignPhone(user, phoneHint);
        if ((user.getEmail() == null || user.getEmail().isBlank()) && claims.email() != null) {
            user.setEmail(claims.email());
        }
        if (!user.isEmailVerified()) {
            user.setEmailVerified(claims.emailVerified());
        }
        if ((user.getDisplayName() == null || user.getDisplayName().isBlank()) && claims.displayName() != null) {
            user.setDisplayName(claims.displayName());
        }
        identity.setEmail(claims.email());
        identity.setEmailVerified(claims.emailVerified());
        identity.setLastLoginAt(OffsetDateTime.now());
        federatedIdentityRepository.save(identity);
        return userRepository.save(user);
    }

    private UserEntity createLinkOrReject(
            GoogleIdTokenVerifierService.GoogleIdentityClaims claims,
            String phoneHint
    ) {
        return userRepository.findByEmailIgnoreCase(claims.email())
                .map(existing -> linkExistingUser(existing, claims, phoneHint))
                .orElseGet(() -> createNewUser(claims, phoneHint));
    }

    private UserEntity linkExistingUser(
            UserEntity user,
            GoogleIdTokenVerifierService.GoogleIdentityClaims claims,
            String phoneHint
    ) {
        if (!authProperties.getGoogle().isAutoLinkByEmail()) {
            throw new ConflictOperationException("errors.auth.googleLinkRequired");
        }

        federatedIdentityRepository.findByUserIdAndProvider(user.getId(), GOOGLE_PROVIDER)
                .ifPresent(existingIdentity -> {
                    if (!claims.subject().equals(existingIdentity.getProviderSubject())) {
                        throw new ConflictOperationException("errors.auth.googleLinkRequired");
                    }
                });

        maybeAssignPhone(user, phoneHint);
        if ((user.getEmail() == null || user.getEmail().isBlank()) && claims.email() != null) {
            user.setEmail(claims.email());
        }
        user.setEmailVerified(claims.emailVerified());
        if ((user.getDisplayName() == null || user.getDisplayName().isBlank()) && claims.displayName() != null) {
            user.setDisplayName(claims.displayName());
        }

        UserEntity savedUser = userRepository.save(user);

        FederatedIdentityEntity identity = federatedIdentityRepository
                .findByUserIdAndProvider(savedUser.getId(), GOOGLE_PROVIDER)
                .orElseGet(FederatedIdentityEntity::new);
        identity.setUserId(savedUser.getId());
        identity.setProvider(GOOGLE_PROVIDER);
        identity.setProviderSubject(claims.subject());
        identity.setEmail(claims.email());
        identity.setEmailVerified(claims.emailVerified());
        identity.setLastLoginAt(OffsetDateTime.now());
        federatedIdentityRepository.save(identity);

        return savedUser;
    }

    private UserEntity createNewUser(
            GoogleIdTokenVerifierService.GoogleIdentityClaims claims,
            String phoneHint
    ) {
        String normalizedPhone = normalizePhone(phoneHint);
        if (normalizedPhone != null && userRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new ConflictOperationException("errors.auth.googlePhoneInUse");
        }

        UserEntity user = new UserEntity();
        user.setPhoneNumber(normalizedPhone);
        user.setEmail(claims.email());
        user.setEmailVerified(true);
        user.setDisplayName(claims.displayName());
        user.setRoleSet(new HashSet<>(Collections.singletonList(Role.NETA)));
        UserEntity savedUser = userRepository.save(user);

        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUserId(savedUser.getId());
        identity.setProvider(GOOGLE_PROVIDER);
        identity.setProviderSubject(claims.subject());
        identity.setEmail(claims.email());
        identity.setEmailVerified(true);
        identity.setLastLoginAt(OffsetDateTime.now());
        federatedIdentityRepository.save(identity);

        return savedUser;
    }

    private void maybeAssignPhone(UserEntity user, String phoneHint) {
        String normalizedPhone = normalizePhone(phoneHint);
        if ((user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) && normalizedPhone == null) {
            if (authProperties.getGoogle().isRequirePhone()) {
                throw new IllegalArgumentException("errors.auth.googlePhoneRequired");
            }
            return;
        }
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return;
        }
        userRepository.findByPhoneNumber(normalizedPhone)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ConflictOperationException("errors.auth.googlePhoneInUse");
                });
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(normalizedPhone);
        }
    }

    private String normalizePhone(String phoneHint) {
        String normalizedPhone = PhoneUtil.normalize(phoneHint);
        if (normalizedPhone == null || normalizedPhone.isBlank()) return null;
        if (!normalizedPhone.matches("^\\+[0-9]{10,15}$")) {
            throw new IllegalArgumentException("validation.phone.invalid");
        }
        return normalizedPhone;
    }
}
