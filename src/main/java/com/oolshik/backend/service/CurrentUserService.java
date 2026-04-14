package com.oolshik.backend.service;

import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity require(AuthenticatedUserPrincipal principal) {
        UserEntity user = resolve(principal);
        if (user == null) {
            throw new IllegalArgumentException("errors.auth.userNotRegistered");
        }
        return user;
    }

    public UserEntity resolve(AuthenticatedUserPrincipal principal) {
        if (principal == null) return null;
        if (principal.userId() != null) {
            var byId = userRepository.findById(principal.userId());
            if (byId.isPresent()) return byId.get();
        }
        if (principal.isFirebaseIdentity() && principal.providerUserId() != null && !principal.providerUserId().isBlank()) {
            var byUid = userRepository.findByFirebaseUid(principal.providerUserId());
            if (byUid.isPresent()) return byUid.get();
        }
        if (principal.phone() != null && !principal.phone().isBlank()) {
            var byPhone = userRepository.findByPhoneNumber(principal.phone());
            if (byPhone.isPresent()) return byPhone.get();
        }
        if (principal.email() != null && !principal.email().isBlank()) {
            return userRepository.findByEmailIgnoreCase(principal.email()).orElse(null);
        }
        return null;
    }
}
