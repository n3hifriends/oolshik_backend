package com.oolshik.backend.service;

import com.oolshik.backend.config.OtpProperties;
import com.oolshik.backend.domain.OtpPurpose;
import com.oolshik.backend.entity.OtpCodeEntity;
import com.oolshik.backend.repo.OtpCodeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpCodeRepository repo;

    @Mock
    private OtpProvider otpProvider;

    @Mock
    private OtpAuditService auditService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private OtpProperties otpProperties;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpProperties = new OtpProperties();
        otpProperties.setProvider("dev");
        when(otpProvider.providerId()).thenReturn("dev");
        otpService = new OtpService(repo, encoder, otpProvider, auditService, otpProperties, new SimpleMeterRegistry());
    }

    @Test
    void requestLoginOtpIncludesDevCodeOnlyWhenEnabled() {
        otpProperties.getDev().setEnabled(true);
        when(repo.findFirstByPhoneNumberOrderByCreatedAtDesc("+919876543210")).thenReturn(Optional.empty());

        Map<String, Object> response = otpService.requestLoginOtp("+919876543210");

        assertThat(response).containsEntry("sent", true);
        assertThat(response).containsKey("devCode");

        ArgumentCaptor<OtpCodeEntity> entityCaptor = ArgumentCaptor.forClass(OtpCodeEntity.class);
        verify(repo).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getProvider()).isEqualTo("dev");
        verify(otpProvider).sendOtp(eq("+919876543210"), any(String.class));
        verify(auditService).record("+919876543210", "dev", "GENERATE", "SUCCESS", null);
        verify(auditService).record("+919876543210", "dev", "DELIVERY", "SUCCESS", null);
    }

    @Test
    void requestLoginOtpOmitsDevCodeWhenDisabled() {
        otpProperties.getDev().setEnabled(false);
        when(repo.findFirstByPhoneNumberOrderByCreatedAtDesc("+919876543210")).thenReturn(Optional.empty());

        Map<String, Object> response = otpService.requestLoginOtp("+919876543210");

        assertThat(response).doesNotContainKey("devCode");
    }

    @Test
    void requestLoginOtpOmitsDevCodeWhenNonDevProviderIsActive() {
        otpProperties.getDev().setEnabled(true);
        when(otpProvider.providerId()).thenReturn("msg91");
        when(repo.findFirstByPhoneNumberOrderByCreatedAtDesc("+919876543210")).thenReturn(Optional.empty());

        Map<String, Object> response = otpService.requestLoginOtp("+919876543210");

        assertThat(response).doesNotContainKey("devCode");
    }

    @Test
    void requestLoginOtpThrowsWhenProviderFails() {
        when(repo.findFirstByPhoneNumberOrderByCreatedAtDesc("+919876543210")).thenReturn(Optional.empty());
        doThrow(new OtpDeliveryException("delivery failed")).when(otpProvider).sendOtp(eq("+919876543210"), any(String.class));

        assertThatThrownBy(() -> otpService.requestLoginOtp("+919876543210"))
                .isInstanceOf(OtpDeliveryException.class);

        verify(auditService).record("+919876543210", "dev", "DELIVERY", "FAILURE", "errors.auth.otpDeliveryFailed");
    }

    @Test
    void verifyLoginOtpConsumesMatchingCode() {
        String code = "123456";
        OtpCodeEntity entity = new OtpCodeEntity();
        entity.setPhoneNumber("+919876543210");
        entity.setProvider("dev");
        entity.setPurpose(OtpPurpose.LOGIN.name());
        entity.setCodeHash(encoder.encode(code));
        entity.setAttemptCount(0);
        entity.setExpiresAt(OffsetDateTime.now().plusMinutes(5));

        when(repo.findActive(eq("+919876543210"), eq(OtpPurpose.LOGIN.name()), any(OffsetDateTime.class)))
                .thenReturn(List.of(entity));

        boolean verified = otpService.verifyLoginOtp("+919876543210", code);

        assertThat(verified).isTrue();
        assertThat(entity.getConsumedAt()).isNotNull();
        verify(auditService).record("+919876543210", "dev", "VERIFY", "SUCCESS", null);
    }

    @Test
    void verifyLoginOtpReturnsFalseForMissingOtp() {
        when(repo.findActive(eq("+919876543210"), eq(OtpPurpose.LOGIN.name()), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        boolean verified = otpService.verifyLoginOtp("+919876543210", "123456");

        assertThat(verified).isFalse();
        verify(auditService).record("+919876543210", "dev", "VERIFY", "FAILURE", "No active OTP");
    }
}
