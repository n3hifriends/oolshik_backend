package com.oolshik.backend.service;

import com.oolshik.backend.config.OtpProperties;
import com.oolshik.backend.domain.OtpPurpose;
import com.oolshik.backend.entity.OtpCodeEntity;
import com.oolshik.backend.repo.OtpCodeRepository;
import com.oolshik.backend.util.PhoneUtil;
import com.oolshik.backend.web.error.OtpCooldownException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository repo;
    private final PasswordEncoder encoder;
    private final OtpProvider otpProvider;
    private final OtpAuditService auditService;
    private final OtpProperties otpProperties;
    private final Counter requestCounter;
    private final Counter requestBlockedCounter;
    private final Counter deliverySuccessCounter;
    private final Counter deliveryFailureCounter;
    private final Counter verifySuccessCounter;
    private final Counter verifyFailureCounter;

    public OtpService(
            OtpCodeRepository repo,
            PasswordEncoder encoder,
            OtpProvider otpProvider,
            OtpAuditService auditService,
            OtpProperties otpProperties,
            MeterRegistry meterRegistry
    ) {
        this.repo = repo;
        this.encoder = encoder;
        this.otpProvider = otpProvider;
        this.auditService = auditService;
        this.otpProperties = otpProperties;
        this.requestCounter = meterRegistry.counter("otp.request.total", "provider", otpProvider.providerId());
        this.requestBlockedCounter = meterRegistry.counter("otp.request.blocked", "provider", otpProvider.providerId());
        this.deliverySuccessCounter = meterRegistry.counter("otp.delivery.success", "provider", otpProvider.providerId());
        this.deliveryFailureCounter = meterRegistry.counter("otp.delivery.failure", "provider", otpProvider.providerId());
        this.verifySuccessCounter = meterRegistry.counter("otp.verify.success", "provider", otpProvider.providerId());
        this.verifyFailureCounter = meterRegistry.counter("otp.verify.failure", "provider", otpProvider.providerId());
    }

    private String genCode() {
        int n = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(n);
    }

    @Transactional
    public Map<String, Object> requestLoginOtp(String rawPhone) {
        String phone = PhoneUtil.normalize(rawPhone);
        OffsetDateTime now = OffsetDateTime.now();
        var last = repo.findFirstByPhoneNumberOrderByCreatedAtDesc(phone).orElse(null);
        if (last != null && last.getLastSentAt() != null &&
            last.getLastSentAt().plusSeconds(otpProperties.getCooldownSeconds()).isAfter(now)) {
            long wait = last.getLastSentAt().plusSeconds(otpProperties.getCooldownSeconds()).toEpochSecond() - now.toEpochSecond();
            auditService.record(phone, resolveProviderId(last), "REQUEST", "COOLDOWN_BLOCKED", "Cooldown in effect");
            requestBlockedCounter.increment();
            throw new OtpCooldownException(Math.max(wait, 1));
        }

        String code = genCode();
        String hash = encoder.encode(code);

        OtpCodeEntity e = new OtpCodeEntity();
        e.setPhoneNumber(phone);
        e.setCodeHash(hash);
        e.setProvider(otpProvider.providerId());
        e.setPurpose(OtpPurpose.LOGIN.name());
        e.setExpiresAt(now.plusSeconds(otpProperties.getTtlSeconds()));
        e.setAttemptCount(0);
        e.setResendCount(last == null ? 0 : last.getResendCount() + 1);
        e.setLastSentAt(now);
        repo.save(e);
        requestCounter.increment();
        auditService.record(phone, otpProvider.providerId(), "GENERATE", "SUCCESS", null);

        try {
            otpProvider.sendOtp(phone, buildMessage(code));
            deliverySuccessCounter.increment();
            auditService.record(phone, otpProvider.providerId(), "DELIVERY", "SUCCESS", null);
        } catch (OtpDeliveryException ex) {
            deliveryFailureCounter.increment();
            auditService.record(phone, otpProvider.providerId(), "DELIVERY", "FAILURE", ex.messageKey());
            throw ex;
        }

        if (otpProperties.getDev().isEnabled() && "dev".equalsIgnoreCase(otpProvider.providerId())) {
            return Map.of("sent", true, "phone", phone, "devCode", code, "ttlSeconds", otpProperties.getTtlSeconds());
        }
        return Map.of("sent", true, "phone", phone, "ttlSeconds", otpProperties.getTtlSeconds());
    }

    @Transactional
    public boolean verifyLoginOtp(String rawPhone, String code) {
        String phone = PhoneUtil.normalize(rawPhone);
        OffsetDateTime now = OffsetDateTime.now();
        List<OtpCodeEntity> list = repo.findActive(phone, OtpPurpose.LOGIN.name(), now);
        if (list.isEmpty()) {
            verifyFailureCounter.increment();
            auditService.record(phone, otpProvider.providerId(), "VERIFY", "FAILURE", "No active OTP");
            return false;
        }
        OtpCodeEntity e = list.get(0);
        if (e.getAttemptCount() >= otpProperties.getMaxAttempts()) {
            verifyFailureCounter.increment();
            auditService.record(phone, resolveProviderId(e), "VERIFY", "FAILURE", "Max attempts exceeded");
            return false;
        }
        e.setAttemptCount(e.getAttemptCount() + 1);
        boolean ok = encoder.matches(code, e.getCodeHash());
        if (ok) e.setConsumedAt(now);
        repo.save(e);
        if (ok) {
            verifySuccessCounter.increment();
        } else {
            verifyFailureCounter.increment();
        }
        auditService.record(phone, resolveProviderId(e), "VERIFY", ok ? "SUCCESS" : "FAILURE", ok ? null : "Code mismatch");
        return ok;
    }

    private String buildMessage(String code) {
        return String.format(
                otpProperties.getMessageTemplate(),
                code,
                Math.max(otpProperties.getTtlSeconds() / 60, 1)
        );
    }

    private String resolveProviderId(OtpCodeEntity entity) {
        return entity.getProvider() == null || entity.getProvider().isBlank()
                ? otpProvider.providerId()
                : entity.getProvider();
    }
}
