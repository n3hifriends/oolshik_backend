package com.oolshik.backend.service;

import com.oolshik.backend.domain.OnboardingPhase;
import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.util.PhoneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.*;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository usersRepo;

    public UserService(UserRepository usersRepo) {
        this.usersRepo = usersRepo;
    }

    @Transactional
    public UserEntity getOrCreateByPhone(String rawPhone, String displayName, String email) {
        String phone = PhoneUtil.normalize(rawPhone);
        String normalizedEmail = normalizeEmail(email);
        Optional<UserEntity> byPhone = usersRepo.findByPhoneNumber(phone);
        if (byPhone.isPresent()) {
            UserEntity existing = byPhone.get();
            applyProfileHints(existing, displayName, normalizedEmail);
            return usersRepo.save(existing);
        }

        if (normalizedEmail != null) {
            Optional<UserEntity> byEmail = usersRepo.findByEmailIgnoreCase(normalizedEmail);
            if (byEmail.isPresent()) {
                UserEntity existing = byEmail.get();
                if (existing.getPhoneNumber() == null || existing.getPhoneNumber().isBlank()) {
                    existing.setPhoneNumber(phone);
                }
                applyProfileHints(existing, displayName, normalizedEmail);
                return usersRepo.save(existing);
            }
        }

        UserEntity ue = new UserEntity();
        ue.setPhoneNumber(phone);
        ue.setDisplayName(displayName);
        ue.setEmail(normalizedEmail);
        ue.setRoleSet(new HashSet<>(Collections.singletonList(Role.NETA)));
        return usersRepo.save(ue);
    }

    @Transactional
    public UserEntity getOrCreate(AuthenticatedUserPrincipal p, @Nullable String displayNameHint, @Nullable String emailHint) {
        if (p.isFirebaseIdentity() && p.providerUserId() != null && !p.providerUserId().isBlank()) {
            Optional<UserEntity> byUid = usersRepo.findByFirebaseUid(p.providerUserId());
            if (byUid.isPresent()) {
                UserEntity existing = byUid.get();
                applyProfileHints(existing, displayNameHint, normalizeEmail(emailHint));
                return usersRepo.save(existing);
            }
        }

        String normalizedEmail = normalizeEmail(emailHint != null ? emailHint : p.email());
        if (normalizedEmail != null) {
            Optional<UserEntity> byEmail = usersRepo.findByEmailIgnoreCase(normalizedEmail);
            if (byEmail.isPresent()) {
                UserEntity existing = byEmail.get();
                if (p.isFirebaseIdentity() && (existing.getFirebaseUid() == null || existing.getFirebaseUid().isBlank())) {
                    existing.setFirebaseUid(p.providerUserId());
                }
                if (existing.getPhoneNumber() == null || existing.getPhoneNumber().isBlank()) {
                    existing.setPhoneNumber(normalizePhone(p.phone()));
                }
                applyProfileHints(existing, displayNameHint, normalizedEmail);
                return usersRepo.save(existing);
            }
        }

        String phone = normalizePhone(p.phone());
        if (phone != null) {
            Optional<UserEntity> byPhone = usersRepo.findByPhoneNumber(phone);
            if (byPhone.isPresent()) {
                UserEntity existing = byPhone.get();
                if (p.isFirebaseIdentity() && (existing.getFirebaseUid() == null || existing.getFirebaseUid().isBlank())) {
                    existing.setFirebaseUid(p.providerUserId());
                }
                applyProfileHints(existing, displayNameHint, normalizedEmail);
                return usersRepo.save(existing);
            }
        }

        UserEntity u = new UserEntity();
        if (p.isFirebaseIdentity()) {
            u.setFirebaseUid(p.providerUserId());
        }
        u.setRoleSet(new HashSet<>(Collections.singletonList(Role.NETA)));
        u.setEmail(normalizedEmail);
        u.setPhoneNumber(phone);  // may be null
        u.setDisplayName(
                (displayNameHint != null && !displayNameHint.isBlank())
                        ? displayNameHint
                        : defaultNameFrom(p)
        );
        return usersRepo.save(u);
    }

    private String normalizePhone(String ph) {
        if (ph == null) return null;
        // store E.164 without spaces
        return ph.replaceAll("\\s+", "");
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void applyProfileHints(UserEntity user, @Nullable String displayName, @Nullable String email) {
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        if ((user.getEmail() == null || user.getEmail().isBlank()) && email != null) {
            user.setEmail(email);
        }
    }

    private String defaultNameFrom(AuthenticatedUserPrincipal p) {
        if (p.email() != null && !p.email().isBlank()) return p.email().split("@")[0];
        if (p.phone() != null && !p.phone().isBlank()) return p.phone();
        return "User";
    }

    /**
     * Advances a user's onboarding phase to the target only if they haven't reached it yet.
     * Runs in its own transaction (REQUIRES_NEW) so a failure here never rolls back the
     * caller's business transaction (task creation, auth-request, completion).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean advanceOnboardingPhase(UUID userId, OnboardingPhase target) {
        return usersRepo.findById(userId).map(user -> {
            OnboardingPhase current = user.getOnboardingPhase() != null ? user.getOnboardingPhase() : OnboardingPhase.FRESH;
            if (!current.isBefore(target)) return false;
            user.setOnboardingPhase(target);
            usersRepo.save(user);
            return true;
        }).orElse(false);
    }
}
