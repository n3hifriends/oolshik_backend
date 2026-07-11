package com.oolshik.backend.service;

import com.oolshik.backend.entity.SystemConfigEntity;
import com.oolshik.backend.repo.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SystemConfigService {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);
    private static final String ZONE_GATE_KEY = "zone.gate.enabled";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final SystemConfigRepository repo;

    private volatile boolean cachedGateEnabled = true;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public SystemConfigService(SystemConfigRepository repo) {
        this.repo = repo;
    }

    public boolean isZoneGateEnabled() {
        Instant now = Instant.now();
        if (now.isBefore(cacheExpiresAt)) {
            return cachedGateEnabled;
        }
        synchronized (this) {
            now = Instant.now();
            if (now.isBefore(cacheExpiresAt)) {
                return cachedGateEnabled;
            }
            cachedGateEnabled = loadGateFromDb();
            cacheExpiresAt = now.plus(CACHE_TTL);
            return cachedGateEnabled;
        }
    }

    @Transactional
    public void setZoneGateEnabled(boolean enabled, UUID updatedBy) {
        SystemConfigEntity row = repo.findById(ZONE_GATE_KEY).orElseGet(() -> {
            SystemConfigEntity e = new SystemConfigEntity();
            e.setKey(ZONE_GATE_KEY);
            return e;
        });
        row.setValue(String.valueOf(enabled));
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(updatedBy);
        repo.save(row);
        synchronized (this) {
            cachedGateEnabled = enabled;
            cacheExpiresAt = Instant.now().plus(CACHE_TTL);
        }
        log.info("Zone gate set to {} by {}", enabled, updatedBy);
    }

    public Map<String, Object> getGateState() {
        var row = repo.findById(ZONE_GATE_KEY);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gateEnabled", row.map(e -> Boolean.parseBoolean(e.getValue())).orElse(true));
        result.put("updatedAt", row.map(SystemConfigEntity::getUpdatedAt).orElse(null));
        result.put("updatedBy", row.map(SystemConfigEntity::getUpdatedBy).orElse(null));
        return result;
    }

    private boolean loadGateFromDb() {
        return repo.findById(ZONE_GATE_KEY)
                .map(e -> Boolean.parseBoolean(e.getValue()))
                .orElse(true);
    }
}
