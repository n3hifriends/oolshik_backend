package com.oolshik.backend.web;

import com.oolshik.backend.config.LocalizationConfig;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.PaymentProfileService;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileMeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, LocalizationConfig.class})
class PaymentProfileControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private PaymentProfileService paymentProfileService;

    @MockBean
    private com.oolshik.backend.security.JwtAuthFilter jwtAuthFilter;

    @Test
    void meReturnsMaskedProfileSummary() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);

        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal("local", null, "+919999999999", "n@example.com", userId);

        when(currentUserService.require(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(paymentProfileService.getMyProfile(userId)).thenReturn(
                new PaymentProfileMeResponse(
                        true,
                        UUID.randomUUID(),
                        "ni******y@ybl",
                        "Nitin",
                        com.oolshik.backend.domain.PaymentProfileSourceType.MANUAL,
                        false,
                        true,
                        null,
                        null
                )
        );

        mockMvc.perform(get("/api/payment-profile/me").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasProfile").value(true))
                .andExpect(jsonPath("$.maskedUpiId").value("ni******y@ybl"))
                .andExpect(jsonPath("$.payeeLabel").value("Nitin"));
    }
}
