package com.oolshik.backend.service;

import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.web.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerLocaleTest {

    @Mock
    private OtpService otpService;
    @Mock
    private UserService userService;
    @Mock
    private AuthService authService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private MessageSource messageSource;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                otpService,
                userService,
                authService,
                userRepository,
                jwtService,
                messageSource
        );
    }

    @Test
    void meReturnsNormalizedPreferredLanguage() {
        FirebaseTokenFilter.FirebaseUserPrincipal principal =
                new FirebaseTokenFilter.FirebaseUserPrincipal("uid-1", "+919999999999", "a@b.com");
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPhoneNumber(principal.phone());
        user.setPreferredLanguage("mr");

        when(userRepository.findByPhoneNumber(principal.phone())).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.me(principal);
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals("mr-IN", body.get("preferredLanguage"));
    }

    @Test
    void updateMeNormalizesPreferredLanguageAndReturnsLocalizedMessage() {
        LocaleContextHolder.resetLocaleContext();
        FirebaseTokenFilter.FirebaseUserPrincipal principal =
                new FirebaseTokenFilter.FirebaseUserPrincipal("uid-2", "+918888888888", "x@y.com");
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPhoneNumber(principal.phone());
        user.setPreferredLanguage("en-IN");

        when(userRepository.findByPhoneNumber(principal.phone())).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("response.updated"), any(), eq("updated"), any()))
                .thenReturn("updated");

        ResponseEntity<?> response = controller.updateMe(principal, Map.of("preferredLanguage", "mr"));
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("mr-IN", userCaptor.getValue().getPreferredLanguage());
        assertEquals("mr-IN", body.get("preferredLanguage"));
        assertEquals("updated", body.get("message"));
    }
}

