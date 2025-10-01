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
        return usersRepo.findByPhoneNumber(phone).orElseGet(() -> {
            UserEntity ue = new UserEntity();
            ue.setPhoneNumber(phone);
            ue.setDisplayName(displayName);
            ue.setEmail(email);
            ue.setRoleSet(new HashSet<>(Collections.singletonList(Role.NETA)));
            return usersRepo.save(ue);
        });
    }

    @Transactional
    public UserEntity getOrCreate(FirebaseTokenFilter.FirebaseUserPrincipal p, @Nullable String displayNameHint, @Nullable String emailHint) {
        return usersRepo.findByFirebaseUid(p.uid()).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setFirebaseUid(p.uid());
            u.setRoleSet(new HashSet<>(Collections.singletonList(Role.NETA)));
//            u.setEmail(p.email());
            u.setEmail(emailHint);
            u.setPhoneNumber(normalizePhone(p.phone()));  // may be null
            u.setDisplayName(
                    (displayNameHint != null && !displayNameHint.isBlank())
                            ? displayNameHint
                            : defaultNameFrom(p)
            );
            // set any defaults: status=ACTIVE, createdAt, etc.
            return usersRepo.save(u);
        });
    }

    private String normalizePhone(String ph) {
        if (ph == null) return null;
        // store E.164 without spaces
        return ph.replaceAll("\\s+", "");
    }

    private String defaultNameFrom(FirebaseTokenFilter.FirebaseUserPrincipal p) {
        if (p.email() != null && !p.email().isBlank()) return p.email().split("@")[0];
        if (p.phone() != null && !p.phone().isBlank()) return p.phone();
        return "User";
    }
}
