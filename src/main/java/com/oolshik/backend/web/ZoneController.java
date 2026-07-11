package com.oolshik.backend.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import com.oolshik.backend.config.ZoneProperties;
import com.oolshik.backend.entity.ServiceZoneEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.ServiceZoneRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.repo.ZoneWaitlistRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.SystemConfigService;
import com.oolshik.backend.service.ZoneCheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/zone")
public class ZoneController {

    private static final int RATE_LIMIT_PER_MINUTE = 10;
    private static final Duration ZONE_CACHE_TTL = Duration.ofSeconds(60);

    private final SystemConfigService systemConfigService;
    private final ServiceZoneRepository serviceZoneRepository;
    private final ZoneCheckService zoneCheckService;
    private final UserRepository userRepository;
    private final ZoneWaitlistRepository waitlistRepository;
    private final CurrentUserService currentUserService;
    private final ZoneProperties zoneProperties;

    private final Cache<UUID, Bucket> rateLimitBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    private volatile List<ServiceZoneEntity> cachedZones = List.of();
    private volatile Instant zoneCacheExpiresAt = Instant.EPOCH;

    public ZoneController(
            SystemConfigService systemConfigService,
            ServiceZoneRepository serviceZoneRepository,
            ZoneCheckService zoneCheckService,
            UserRepository userRepository,
            ZoneWaitlistRepository waitlistRepository,
            CurrentUserService currentUserService,
            ZoneProperties zoneProperties
    ) {
        this.systemConfigService = systemConfigService;
        this.serviceZoneRepository = serviceZoneRepository;
        this.zoneCheckService = zoneCheckService;
        this.userRepository = userRepository;
        this.waitlistRepository = waitlistRepository;
        this.currentUserService = currentUserService;
        this.zoneProperties = zoneProperties;
    }

    @PostMapping("/check")
    public ResponseEntity<?> checkZone(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        if (principal == null) return ResponseEntity.status(401).build();

        Bucket bucket = rateLimitBuckets.get(principal.userId(), id ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(RATE_LIMIT_PER_MINUTE,
                                Refill.intervally(RATE_LIMIT_PER_MINUTE, Duration.ofMinutes(1))))
                        .build());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limit_exceeded"));
        }

        double lat, lng;
        try {
            lat = toDouble(body.get("lat"));
            lng = toDouble(body.get("lng"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_coordinates"));
        }

        if (!systemConfigService.isZoneGateEnabled()) {
            Map<String, Object> gateOff = new LinkedHashMap<>();
            gateOff.put("eligible", true);
            gateOff.put("zoneName", null);
            return ResponseEntity.ok(gateOff);
        }

        List<ServiceZoneEntity> activeZones = getActiveZones();

        if (activeZones.isEmpty() && zoneProperties.isBypassWhenNoZonesActive()) {
            // No zones configured yet — write zoneConfirmed=true so the phase gate is not blocked
            writeZoneCache(principal, true, null);
            Map<String, Object> bypass = new LinkedHashMap<>();
            bypass.put("eligible", true);
            bypass.put("zoneName", null);
            return ResponseEntity.ok(bypass);
        }

        var match = zoneCheckService.findMatchingZone(activeZones, lat, lng);
        writeZoneCache(principal, match.isPresent(), match.map(ServiceZoneEntity::getId).orElse(null));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eligible", match.isPresent());
        result.put("zoneName", match.map(ServiceZoneEntity::getName).orElse(null));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/waitlist")
    public ResponseEntity<?> joinWaitlist(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        if (principal == null) return ResponseEntity.status(401).build();

        double lat, lng;
        try {
            lat = round4(toDouble(body.get("lat")));
            lng = round4(toDouble(body.get("lng")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_coordinates"));
        }

        waitlistRepository.upsert(principal.userId(), lat, lng);
        return ResponseEntity.ok(Map.of("queued", true));
    }

    private void writeZoneCache(AuthenticatedUserPrincipal principal, boolean confirmed, UUID zoneId) {
        UserEntity user = currentUserService.require(principal);
        user.setZoneConfirmed(confirmed);
        user.setConfirmedZoneId(zoneId);
        user.setZoneConfirmedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    private List<ServiceZoneEntity> getActiveZones() {
        if (Instant.now().isBefore(zoneCacheExpiresAt)) return cachedZones;
        synchronized (this) {
            if (Instant.now().isBefore(zoneCacheExpiresAt)) return cachedZones;
            cachedZones = serviceZoneRepository.findByActiveTrue();
            zoneCacheExpiresAt = Instant.now().plus(ZONE_CACHE_TTL);
            return cachedZones;
        }
    }

    private double toDouble(Object value) {
        if (value == null) throw new IllegalArgumentException("coordinate is null");
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid coordinate: " + value);
        }
    }

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
