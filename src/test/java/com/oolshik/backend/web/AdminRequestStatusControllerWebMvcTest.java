package com.oolshik.backend.web;

import com.oolshik.backend.admin.AdminController;
import com.oolshik.backend.admin.AdminDtos.AdminRequestDetail;
import com.oolshik.backend.admin.AdminDtos.GeoPoint;
import com.oolshik.backend.admin.AdminDtos.UserRef;
import com.oolshik.backend.admin.AdminService;
import com.oolshik.backend.config.AuthProperties;
import com.oolshik.backend.config.CommonBeans;
import com.oolshik.backend.config.LocalizationConfig;
import com.oolshik.backend.domain.Role;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.JwtAuthFilter;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.security.SecurityConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthFilter.class, CommonBeans.class, GlobalExceptionHandler.class, LocalizationConfig.class})
@TestPropertySource(properties = {
        "firebase.project-id=test-project",
        "app.security.identity-provider=local",
        "app.cors.allowedOrigins[0]=https://www.oolshik.in"
})
class AdminRequestStatusControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuthProperties authProperties;

    private static final String ENDPOINT = "/api/admin/requests/{id}/status";

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(patch(ENDPOINT, UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\",\"note\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminReturns403() throws Exception {
        UUID userId = UUID.randomUUID();
        stubUser(userId, "neta-token", Role.NETA);

        mockMvc.perform(patch(ENDPOINT, UUID.randomUUID())
                        .header("Authorization", "Bearer neta-token")
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\",\"note\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminWithValidRequestReturns200() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        stubUser(adminId, "admin-token", Role.ADMIN);

        AdminRequestDetail detail = new AdminRequestDetail(
                requestId, "Fix leak", "desc", "COMPLETED",
                new UserRef(UUID.randomUUID(), "Requester", "+91999"),
                null,
                new GeoPoint(12.9, 77.6),
                500, null, "INR", OffsetDateTime.now(), null, null, null
        );
        when(adminService.adminUpdateHelpRequestStatus(eq(requestId), eq("COMPLETED"), eq("Admin closed"), eq(adminId)))
                .thenReturn(detail);

        mockMvc.perform(patch(ENDPOINT, requestId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\",\"note\":\"Admin closed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void adminWithBadStatusReturns400() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        stubUser(adminId, "admin-token-2", Role.ADMIN);

        when(adminService.adminUpdateHelpRequestStatus(eq(requestId), eq("ASSIGNED"), any(), eq(adminId)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status override not supported in v1: ASSIGNED"));

        mockMvc.perform(patch(ENDPOINT, requestId)
                        .header("Authorization", "Bearer admin-token-2")
                        .contentType("application/json")
                        .content("{\"status\":\"ASSIGNED\",\"note\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    private void stubUser(UUID userId, String token, Role role) {
        Claims claims = mock(Claims.class);
        Jws<Claims> jws = mock(Jws.class);
        when(jwtService.parse(token)).thenReturn(jws);
        when(jws.getBody()).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn(userId.toString());

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+919876543210");
        user.setRoleSet(Set.of(role));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }
}
