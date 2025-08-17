package com.oolshik.backend.service;

import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@DependsOn("entityManagerFactory")
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;
    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @PostConstruct
    public void seedAdmin() {
        if (adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
            userRepository.findByEmail(adminEmail).orElseGet(() -> {
                UserEntity e = new UserEntity();
                e.setEmail(adminEmail);
                e.setPhoneNumber("+910000000000");
                e.setPasswordHash(encoder.encode(adminPassword));
                e.setDisplayName("Admin");
                e.setRoleSet(new HashSet<>(Arrays.asList(Role.ADMIN, Role.NETA, Role.KARYAKARTA)));
                return userRepository.save(e);
            });
        }
    }

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        UserEntity ue = userRepository.findByPhoneNumber(phone)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Use the concrete list type SimpleGrantedAuthority to avoid generics mismatch
        java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> auths =
            ue.getRoleSet().stream()
            .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r.name()))
            .toList();

        return new org.springframework.security.core.userdetails.User(
            ue.getPhoneNumber(),
            ue.getPasswordHash() == null ? "" : ue.getPasswordHash(),
            auths  // OK: constructor accepts Collection<? extends GrantedAuthority>
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loginWithPassword(String email, String password) {
        UserEntity ue = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (ue.getPasswordHash() == null || !encoder.matches(password, ue.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String access = jwtService.generateAccessToken(ue.getId(), ue.getPhoneNumber());
        String refresh = jwtService.generateRefreshToken(ue.getId());
        return Map.of("userId", ue.getId(), "accessToken", access, "refreshToken", refresh);
    }

    public String refreshAccessToken(String refreshToken) {
        var jws = jwtService.parse(refreshToken);
        String typ = jws.getBody().get("typ", String.class);
        if (!"refresh".equals(typ)) throw new IllegalArgumentException("Not a refresh token");
        UUID userId = UUID.fromString(jws.getBody().getSubject());
        UserEntity ue = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User missing"));
        return jwtService.generateAccessToken(ue.getId(), ue.getPhoneNumber());
    }
}
