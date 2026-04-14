package com.oolshik.backend.web;

import com.oolshik.backend.config.AuthProperties;
import com.oolshik.backend.config.LocalizationConfig;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtAuthFilter;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.security.SecurityConfig;
import com.oolshik.backend.service.AuthService;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.GoogleAuthService;
import com.oolshik.backend.service.OtpService;
import com.oolshik.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class, LocalizationConfig.class})
@TestPropertySource(properties = {
        "firebase.project-id=test-project",
        "app.security.identity-provider=local"
})
class AuthSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OtpService otpService;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private GoogleAuthService googleAuthService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private AuthProperties authProperties;

    @Test
    void meReturnsUnauthorizedWhenBearerTokenCannotBeParsed() throws Exception {
        when(jwtService.parse("expired-token")).thenThrow(new RuntimeException("expired"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }
}
