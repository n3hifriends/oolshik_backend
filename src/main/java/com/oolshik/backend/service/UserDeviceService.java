package com.oolshik.backend.service;

import com.oolshik.backend.entity.UserDeviceEntity;
import com.oolshik.backend.repo.UserDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserDeviceService {

    private static final Pattern EXPO_TOKEN_PATTERN =
            Pattern.compile("^(ExponentPushToken|ExpoPushToken)\\[[^\\]]+\\]$");

    private final UserDeviceRepository repository;

    public UserDeviceService(UserDeviceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registerDevice(UUID userId, String token, String platform) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        if (!EXPO_TOKEN_PATTERN.matcher(token).matches()) {
            throw new IllegalArgumentException("invalid Expo push token");
        }
        String hash = sha256(token);
        UserDeviceEntity entity = repository.findByTokenHash(hash).orElseGet(UserDeviceEntity::new);
        entity.setUserId(userId);
        entity.setProvider("EXPO");
        entity.setPlatform(platform == null || platform.isBlank() ? null : platform.toUpperCase());
        entity.setToken(token);
        entity.setTokenHash(hash);
        entity.setActive(true);
        entity.setLastSeenAt(OffsetDateTime.now());
        repository.save(entity);
    }

    @Transactional
    public void unregisterDevice(UUID userId, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        if (!EXPO_TOKEN_PATTERN.matcher(token).matches()) {
            throw new IllegalArgumentException("invalid Expo push token");
        }
        String hash = sha256(token);
        repository.findByTokenHash(hash).ifPresent((entity) -> {
            if (entity.getUserId() != null && entity.getUserId().equals(userId)) {
                if (entity.isActive()) {
                    entity.setActive(false);
                    repository.save(entity);
                }
            }
        });
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
