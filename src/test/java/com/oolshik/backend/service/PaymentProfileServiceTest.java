package com.oolshik.backend.service;

import com.oolshik.backend.domain.PaymentProfileSourceType;
import com.oolshik.backend.entity.PaymentProfileEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.PaymentProfileRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileUpsertRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProfileServiceTest {

    @Mock
    private PaymentProfileRepository paymentProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void upsertNormalizesUpiIdAndFallsBackToDisplayName() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName("Nitin Kalokhe");

        when(paymentProfileRepository.findActiveForUpdate(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentProfileRepository.save(any(PaymentProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, PaymentProfileEntity.class));

        PaymentProfileService service = new PaymentProfileService(paymentProfileRepository, userRepository);
        PaymentProfileEntity saved = service.upsert(
                userId,
                new PaymentProfileUpsertRequest("  Nitin.Pay@YBL ", null, PaymentProfileSourceType.MANUAL)
        );

        ArgumentCaptor<PaymentProfileEntity> captor = ArgumentCaptor.forClass(PaymentProfileEntity.class);
        verify(paymentProfileRepository).save(captor.capture());
        assertEquals("nitin.pay@ybl", captor.getValue().getUpiId());
        assertEquals("Nitin Kalokhe", captor.getValue().getPayeeLabel());
        assertEquals("nitin.pay@ybl", service.toEditResponse(saved).upiId());
        assertEquals("ni******y@ybl", service.toResponse(saved).maskedUpiId());
    }
}
