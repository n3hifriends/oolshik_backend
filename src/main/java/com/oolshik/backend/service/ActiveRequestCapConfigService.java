package com.oolshik.backend.service;

import com.oolshik.backend.repo.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class ActiveRequestCapConfigService {

    public static final String MAX_ACTIVE_REQUESTS_PER_REQUESTER_KEY = "MAX_ACTIVE_REQUESTS_PER_REQUESTER";

    private static final Logger log = LoggerFactory.getLogger(ActiveRequestCapConfigService.class);
    private static final int DEFAULT_CAP = 2;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final AppConfigRepository appConfigRepository;

    private volatile int cachedCap = DEFAULT_CAP;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public ActiveRequestCapConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    public int getMaxActiveRequestsPerRequester() {
        Instant now = Instant.now();
        if (now.isBefore(cacheExpiresAt)) {
            return cachedCap;
        }

        synchronized (this) {
            now = Instant.now();
            if (now.isBefore(cacheExpiresAt)) {
                return cachedCap;
            }
            cachedCap = loadCapFromConfig();
            cacheExpiresAt = now.plus(CACHE_TTL);
            return cachedCap;
        }
    }

    private int loadCapFromConfig() {
        String raw = appConfigRepository.findValueByKey(MAX_ACTIVE_REQUESTS_PER_REQUESTER_KEY).orElse(null);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CAP;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed >= 1) {
                return parsed;
            }
            log.warn("Invalid {} config value '{}'; using fallback {}", MAX_ACTIVE_REQUESTS_PER_REQUESTER_KEY, raw, DEFAULT_CAP);
        } catch (NumberFormatException ex) {
            log.warn("Failed parsing {}='{}'; using fallback {}", MAX_ACTIVE_REQUESTS_PER_REQUESTER_KEY, raw, DEFAULT_CAP);
        }
        return DEFAULT_CAP;
    }
}
