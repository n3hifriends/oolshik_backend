package com.oolshik.backend.service;

import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.util.PhoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.util.*;

@Service
public class UserService {

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
            Optional<UserEntity> byEmail = usersRepo.findByEmail(normalizedEmail);
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
    public UserEntity getOrCreate(FirebaseTokenFilter.FirebaseUserPrincipal p, @Nullable String displayNameHint, @Nullable String emailHint) {
        Optional<UserEntity> byUid = usersRepo.findByFirebaseUid(p.uid());
        if (byUid.isPresent()) {
            UserEntity existing = byUid.get();
            applyProfileHints(existing, displayNameHint, normalizeEmail(emailHint));
            return usersRepo.save(existing);
        }

        String normalizedEmail = normalizeEmail(emailHint);
        if (normalizedEmail != null) {
            Optional<UserEntity> byEmail = usersRepo.findByEmail(normalizedEmail);
            if (byEmail.isPresent()) {
                UserEntity existing = byEmail.get();
                if (existing.getFirebaseUid() == null || existing.getFirebaseUid().isBlank()) {
                    existing.setFirebaseUid(p.uid());
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
                if (existing.getFirebaseUid() == null || existing.getFirebaseUid().isBlank()) {
                    existing.setFirebaseUid(p.uid());
                }
                applyProfileHints(existing, displayNameHint, normalizedEmail);
                return usersRepo.save(existing);
            }
        }

        UserEntity u = new UserEntity();
        u.setFirebaseUid(p.uid());
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
        String trimmed = email.trim();
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

    private String defaultNameFrom(FirebaseTokenFilter.FirebaseUserPrincipal p) {
        if (p.email() != null && !p.email().isBlank()) return p.email().split("@")[0];
        if (p.phone() != null && !p.phone().isBlank()) return p.phone();
        return "User";
    }
}
