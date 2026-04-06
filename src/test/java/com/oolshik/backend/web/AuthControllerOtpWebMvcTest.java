package com.oolshik.backend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.backend.config.LocalizationConfig;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.service.AuthService;
import com.oolshik.backend.service.OtpService;
import com.oolshik.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, LocalizationConfig.class})
class AuthControllerOtpWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void otpRequestPreservesContract() throws Exception {
        when(otpService.requestLoginOtp("+919876543210"))
                .thenReturn(Map.of("sent", true, "phone", "+919876543210", "ttlSeconds", 300));

        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "+919876543210"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.phone").value("+919876543210"))
                .andExpect(jsonPath("$.ttlSeconds").value(300));
    }

    @Test
    void otpVerifyReturnsTokensOnSuccess() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPhoneNumber("+919876543210");

        when(otpService.verifyLoginOtp("+919876543210", "123456")).thenReturn(true);
        when(userService.getOrCreateByPhone("+919876543210", "Nitin", "n@example.com")).thenReturn(user);
        when(jwtService.generateAccessToken(user.getId(), user.getPhoneNumber())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user.getId())).thenReturn("refresh-token");

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", "+919876543210",
                                "code", "123456",
                                "displayName", "Nitin",
                                "email", "n@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }
}
