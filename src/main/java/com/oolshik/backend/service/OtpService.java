package com.oolshik.backend.service;

import com.oolshik.backend.domain.OtpPurpose;
import com.oolshik.backend.entity.OtpCodeEntity;
import com.oolshik.backend.repo.OtpCodeRepository;
import com.oolshik.backend.util.PhoneUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class OtpService {

    private final OtpCodeRepository repo;
    private final PasswordEncoder encoder;
    private final SmsSender smsSender;

    private final int ttlSeconds;
    private final int cooldownSeconds;
    private final int maxAttempts;
    private final boolean dev;

    public OtpService(
            OtpCodeRepository repo,
            PasswordEncoder encoder,
            SmsSender smsSender,
            @Value("${app.otp.ttlSeconds:300}") int ttlSeconds,
            @Value("${app.otp.cooldownSeconds:30}") int cooldownSeconds,
            @Value("${app.otp.maxAttempts:5}") int maxAttempts,
            @Value("${spring.profiles.active:}") String activeProfile
    ) {
        this.repo = repo;
        this.encoder = encoder;
        this.smsSender = smsSender;
        this.ttlSeconds = ttlSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.maxAttempts = maxAttempts;
        this.dev = activeProfile != null && activeProfile.contains("dev");
    }

    private String genCode() {
        int n = new Random().nextInt(900000) + 100000;
        return String.valueOf(n);
    }

    @Transactional
    public Map<String, Object> requestLoginOtp(String rawPhone) {
        String phone = PhoneUtil.normalize(rawPhone);
        OffsetDateTime now = OffsetDateTime.now();
        var last = repo.findFirstByPhoneNumberOrderByCreatedAtDesc(phone).orElse(null);
        if (last != null && last.getLastSentAt() != null &&
            last.getLastSentAt().plusSeconds(cooldownSeconds).isAfter(now)) {
            long wait = last.getLastSentAt().plusSeconds(cooldownSeconds).toEpochSecond() - now.toEpochSecond();
            throw new IllegalStateException("Please wait " + Math.max(wait, 1) + "s before requesting another OTP");
        }

        String code = genCode();
        String hash = encoder.encode(code);

        OtpCodeEntity e = new OtpCodeEntity();
        e.setPhoneNumber(phone);
        e.setCodeHash(hash);
        e.setPurpose(OtpPurpose.LOGIN.name());
        e.setExpiresAt(now.plusSeconds(ttlSeconds));
        e.setAttemptCount(0);
        e.setResendCount(last == null ? 0 : last.getResendCount() + 1);
        e.setLastSentAt(now);
        repo.save(e);

        smsSender.send(phone, "Your Oolshik login code: " + code + " (valid " + (ttlSeconds/60) + " min)");

        if (dev) return Map.of("sent", true, "phone", phone, "devCode", code, "ttlSeconds", ttlSeconds);
        return Map.of("sent", true, "phone", phone, "ttlSeconds", ttlSeconds);
    }

    @Transactional
    public boolean verifyLoginOtp(String rawPhone, String code) {
        String phone = PhoneUtil.normalize(rawPhone);
        OffsetDateTime now = OffsetDateTime.now();
        List<OtpCodeEntity> list = repo.findActive(phone, OtpPurpose.LOGIN.name(), now);
        if (list.isEmpty()) return false;
        OtpCodeEntity e = list.get(0);
        if (e.getAttemptCount() >= maxAttempts) return false;
        e.setAttemptCount(e.getAttemptCount() + 1);
        boolean ok = encoder.matches(code, e.getCodeHash());
        if (ok) e.setConsumedAt(now);
        repo.save(e);
        return ok;
    }
}
