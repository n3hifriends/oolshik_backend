package com.oolshik.backend.service;

import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.web.error.AccountBlockedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceBlockUserTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @Test
    void blockedUserCannotPasswordLogin() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@example.com");
        user.setPasswordHash("hash");
        user.setBlocked(true);

        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThrows(
                AccountBlockedException.class,
                () -> service.loginWithPassword("admin@example.com", "secret")
        );
        verify(jwtService, never()).generateAccessToken(user.getId(), user.getPhoneNumber());
    }

    @Test
    void blockedUserCannotRefreshAccessToken() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        Jws<Claims> jws = mock(Jws.class);
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setBlocked(true);

        when(jwtService.parse("refresh-token")).thenReturn(jws);
        when(jws.getBody()).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(AccountBlockedException.class, () -> service.refreshAccessToken("refresh-token"));
        verify(jwtService, never()).generateAccessToken(user.getId(), user.getPhoneNumber());
    }
}
