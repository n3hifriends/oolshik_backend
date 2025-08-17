package com.oolshik.backend.service;

import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.util.PhoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class UserService {

    private final UserRepository repo;
    public UserService(UserRepository repo) { this.repo = repo; }

    @Transactional
    public UserEntity getOrCreateByPhone(String rawPhone, String displayName, String email) {
        String phone = PhoneUtil.normalize(rawPhone);
        return repo.findByPhoneNumber(phone).orElseGet(() -> {
            UserEntity ue = new UserEntity();
            ue.setPhoneNumber(phone);
            ue.setDisplayName(displayName);
            ue.setEmail(email);
            ue.setRoleSet(new HashSet<>(Collections.singletonList(Role.NETA)));
            return repo.save(ue);
        });
    }
}
