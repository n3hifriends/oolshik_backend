package com.oolshik.backend.web;

import com.oolshik.backend.config.AuthProperties;
import com.oolshik.backend.config.CommonBeans;
import com.oolshik.backend.config.LocalizationConfig;
import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtAuthFilter;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.security.SecurityConfig;
import com.oolshik.backend.service.AuthService;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.GoogleAuthService;
import com.oolshik.backend.service.OtpService;
import com.oolshik.backend.service.SystemConfigService;
import com.oolshik.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthFilter.class, CommonBeans.class, GlobalExceptionHandler.class, LocalizationConfig.class})
@TestPropertySource(properties = {
        "firebase.project-id=test-project",
        "app.security.identity-provider=local",
        "app.cors.allowedOrigins[0]=https://www.oolshik.in"
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

    @MockBean
    private SystemConfigService systemConfigService;

    @Test
    void meReturnsUnauthorizedWhenBearerTokenCannotBeParsed() throws Exception {
        when(jwtService.parse("expired-token")).thenThrow(new RuntimeException("expired"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockedUserJwtIsRejectedBeforeController() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+919876543210");
        user.setRoleSet(Set.of(Role.NETA));
        user.setBlocked(true);
        stubAccessToken("blocked-token", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer blocked-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("ACCOUNT_BLOCKED"));
    }

    @Test
    void deletedUserJwtIsRejectedBeforeController() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRoleSet(Set.of(Role.NETA));
        user.setDeleted(true);
        stubAccessToken("deleted-token", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer deleted-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("ACCOUNT_DELETED"));
    }

    @Test
    void deleteMeInvokesUserServiceAndReturnsNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+919876543210");
        user.setRoleSet(Set.of(Role.NETA));
        stubAccessToken("delete-me-token", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(currentUserService.require(org.mockito.ArgumentMatchers.any())).thenReturn(user);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/me")
                        .header("Authorization", "Bearer delete-me-token"))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(userService).deleteOwnAccount(userId);
    }

    @Test
    void nonAdminCannotCallBlockEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+919876543210");
        user.setRoleSet(Set.of(Role.NETA));
        stubAccessToken("neta-token", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(patch("/api/admin/users/" + targetId + "/block")
                        .header("Authorization", "Bearer neta-token")
                        .contentType("application/json")
                        .content("{\"reason\":\"policy\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotCallUnblockEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+919876543210");
        user.setRoleSet(Set.of(Role.NETA));
        stubAccessToken("neta-unblock-token", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(patch("/api/admin/users/" + targetId + "/unblock")
                        .header("Authorization", "Bearer neta-unblock-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginPreflightIsAllowedForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://www.oolshik.in")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.oolshik.in"));
    }

    @Test
    void adminPatchPreflightIsAllowedForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/admin/reports/00000000-0000-0000-0000-000000000000/status")
                        .header("Origin", "https://www.oolshik.in")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.oolshik.in"));
    }

    private void stubAccessToken(String token, UUID userId) {
        Claims claims = mock(Claims.class);
        Jws<Claims> jws = mock(Jws.class);
        when(jwtService.parse(token)).thenReturn(jws);
        when(jws.getBody()).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn(userId.toString());
    }
}
