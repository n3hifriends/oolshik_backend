package com.oolshik.backend.service;

import com.oolshik.backend.domain.PaymentProfileSourceType;
import com.oolshik.backend.entity.PaymentProfileEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.PaymentProfileRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.util.MaskingUtils;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileEditResponse;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileMeResponse;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileUpsertRequest;
import com.oolshik.backend.web.error.ConflictOperationException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentProfileService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProfileService.class);
    private static final Pattern UPI_ID_PATTERN = Pattern.compile(
            "^[a-z0-9][a-z0-9._-]{1,127}@[a-z0-9][a-z0-9.-]{1,63}$"
    );

    private final PaymentProfileRepository paymentProfileRepository;
    private final UserRepository userRepository;

    public PaymentProfileService(
            PaymentProfileRepository paymentProfileRepository,
            UserRepository userRepository
    ) {
        this.paymentProfileRepository = paymentProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentProfileEntity> getActiveProfile(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return paymentProfileRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public PaymentProfileMeResponse getMyProfile(UUID userId) {
        return getActiveProfile(userId)
                .map(this::toResponse)
                .orElse(new PaymentProfileMeResponse(
                        false,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        null,
                        null
                ));
    }

    @Transactional
    public PaymentProfileEntity create(UUID userId, PaymentProfileUpsertRequest request) {
        if (paymentProfileRepository.findActiveForUpdate(userId).isPresent()) {
            throw new ConflictOperationException("errors.paymentProfile.alreadyExists");
        }
        return saveNewProfile(userId, request);
    }

    @Transactional
    public PaymentProfileEntity upsert(UUID userId, PaymentProfileUpsertRequest request) {
        PaymentProfileEntity profile = paymentProfileRepository.findActiveForUpdate(userId)
                .orElseGet(() -> newPaymentProfile(userId));
        return applyAndSave(profile, userId, request);
    }

    @Transactional
    public void delete(UUID userId) {
        paymentProfileRepository.findActiveForUpdate(userId).ifPresent(profile -> {
            profile.setActive(false);
            profile.setUpdatedAt(OffsetDateTime.now());
            paymentProfileRepository.save(profile);
            log.info(
                    "Payment profile deactivated userId={} upi={}",
                    userId,
                    MaskingUtils.maskUpiId(profile.getUpiId())
            );
        });
    }

    @Transactional(readOnly = true)
    public PaymentProfileEntity requireActiveProfile(UUID userId) {
        return getActiveProfile(userId)
                .orElseThrow(() -> new ConflictOperationException("errors.paymentProfile.missing"));
    }

    @Transactional(readOnly = true)
    public String resolvePayeeLabel(UUID userId, PaymentProfileEntity profile) {
        String explicitLabel = normalizeLabel(profile.getPayeeLabel());
        if (explicitLabel != null) {
            return explicitLabel;
        }
        return userRepository.findById(userId)
                .map(UserEntity::getDisplayName)
                .map(this::normalizeLabel)
                .orElse("Oolshik");
    }

    public PaymentProfileMeResponse toResponse(PaymentProfileEntity profile) {
        if (profile == null || !profile.isActive()) {
            return new PaymentProfileMeResponse(false, null, null, null, null, false, false, null, null);
        }
        return new PaymentProfileMeResponse(
                true,
                profile.getId(),
                MaskingUtils.maskUpiId(profile.getUpiId()),
                profile.getPayeeLabel(),
                profile.getSourceType(),
                profile.isVerified(),
                profile.isActive(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    public PaymentProfileEditResponse toEditResponse(PaymentProfileEntity profile) {
        if (profile == null || !profile.isActive()) {
            return new PaymentProfileEditResponse(false, null, null, null, null, null, false, false, null, null);
        }
        return new PaymentProfileEditResponse(
                true,
                profile.getId(),
                profile.getUpiId(),
                MaskingUtils.maskUpiId(profile.getUpiId()),
                profile.getPayeeLabel(),
                profile.getSourceType(),
                profile.isVerified(),
                profile.isActive(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private PaymentProfileEntity saveNewProfile(UUID userId, PaymentProfileUpsertRequest request) {
        return applyAndSave(newPaymentProfile(userId), userId, request);
    }

    private PaymentProfileEntity newPaymentProfile(UUID userId) {
        PaymentProfileEntity profile = new PaymentProfileEntity();
        profile.setUserId(userId);
        profile.setActive(true);
        profile.setVerified(false);
        return profile;
    }

    private PaymentProfileEntity applyAndSave(
            PaymentProfileEntity profile,
            UUID userId,
            PaymentProfileUpsertRequest request
    ) {
        String normalizedUpiId = normalizeUpiId(request.upiId());
        String payeeLabel = normalizeLabel(request.payeeLabel());
        PaymentProfileSourceType sourceType =
                request.sourceType() == null ? PaymentProfileSourceType.MANUAL : request.sourceType();

        profile.setUpiId(normalizedUpiId);
        profile.setPayeeLabel(resolveStoredPayeeLabel(userId, payeeLabel));
        profile.setSourceType(sourceType);
        profile.setActive(true);
        profile.setUpdatedAt(OffsetDateTime.now());

        PaymentProfileEntity saved = paymentProfileRepository.save(profile);
        log.info(
                "Payment profile saved userId={} sourceType={} upi={}",
                userId,
                sourceType,
                MaskingUtils.maskUpiId(saved.getUpiId())
        );
        return saved;
    }

    private String resolveStoredPayeeLabel(UUID userId, String requestedLabel) {
        if (requestedLabel != null) {
            return requestedLabel;
        }
        return userRepository.findById(userId)
                .map(UserEntity::getDisplayName)
                .map(this::normalizeLabel)
                .orElse(null);
    }

    private String normalizeUpiId(String rawUpiId) {
        if (rawUpiId == null) {
            throw new IllegalArgumentException("errors.paymentProfile.invalidUpiId");
        }
        String normalized = rawUpiId.trim().toLowerCase(Locale.ROOT);
        if (!UPI_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("errors.paymentProfile.invalidUpiId");
        }
        return normalized;
    }

    private String normalizeLabel(String rawLabel) {
        if (rawLabel == null) {
            return null;
        }
        String normalized = rawLabel.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
