package com.oolshik.backend.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.backend.entity.NotificationOutboxEntity;
import com.oolshik.backend.entity.ServiceZoneEntity;
import com.oolshik.backend.entity.ZoneWaitlistEntity;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.notification.NotificationOutboxStatus;
import com.oolshik.backend.repo.NotificationOutboxRepository;
import com.oolshik.backend.repo.ServiceZoneRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.repo.ZoneWaitlistRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.service.SystemConfigService;
import com.oolshik.backend.service.ZoneCheckService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/zone")
public class AdminZoneController {

    private final ServiceZoneRepository zoneRepository;
    private final ZoneWaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final ZoneCheckService zoneCheckService;
    private final SystemConfigService systemConfigService;
    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdminZoneController(
            ServiceZoneRepository zoneRepository,
            ZoneWaitlistRepository waitlistRepository,
            UserRepository userRepository,
            ZoneCheckService zoneCheckService,
            SystemConfigService systemConfigService,
            NotificationOutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.zoneRepository = zoneRepository;
        this.waitlistRepository = waitlistRepository;
        this.userRepository = userRepository;
        this.zoneCheckService = zoneCheckService;
        this.systemConfigService = systemConfigService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> listZones(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(zoneRepository.findAll(pageable));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createZone(@RequestBody Map<String, Object> body) {
        List<String> errors = validateZoneBody(body);
        if (!errors.isEmpty()) return ResponseEntity.badRequest().body(Map.of("errors", errors));

        ServiceZoneEntity zone = applyZoneFields(new ServiceZoneEntity(), body);
        zoneRepository.save(zone);
        return ResponseEntity.status(201).body(zone);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateZone(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        ServiceZoneEntity zone = zoneRepository.findById(id).orElse(null);
        if (zone == null) return ResponseEntity.notFound().build();

        List<String> errors = validateZoneBody(body);
        if (!errors.isEmpty()) return ResponseEntity.badRequest().body(Map.of("errors", errors));

        boolean wasActive = zone.isActive();
        applyZoneFields(zone, body);
        zone = zoneRepository.save(zone); // commits in its own transaction

        // Re-engagement runs after the zone save commits so a notification failure
        // cannot roll back the zone activation.
        if (!wasActive && zone.isActive()) {
            triggerWaitlistReengagement(zone);
        }

        return ResponseEntity.ok(zone);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patchZone(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        ServiceZoneEntity zone = zoneRepository.findById(id).orElse(null);
        if (zone == null) return ResponseEntity.notFound().build();

        List<String> errors = new ArrayList<>();

        if (body.containsKey("name")) {
            String name = getString(body, "name");
            if (name == null || name.isBlank()) errors.add("name must not be blank");
            else if (name.length() > 128) errors.add("name must be at most 128 characters");
            else zone.setName(name);
        }

        if (body.containsKey("type")) {
            List<String> geomErrors = validateZoneBody(body);
            if (!geomErrors.isEmpty()) return ResponseEntity.badRequest().body(Map.of("errors", geomErrors));
            applyZoneFields(zone, body);
        }

        if (!errors.isEmpty()) return ResponseEntity.badRequest().body(Map.of("errors", errors));

        boolean wasActive = zone.isActive();
        if (body.containsKey("active")) {
            zone.setActive(Boolean.TRUE.equals(body.get("active")));
        }

        zone = zoneRepository.save(zone);

        if (!wasActive && zone.isActive()) {
            triggerWaitlistReengagement(zone);
        }

        return ResponseEntity.ok(zone);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deactivateZone(@PathVariable UUID id) {
        ServiceZoneEntity zone = zoneRepository.findById(id).orElse(null);
        if (zone == null) return ResponseEntity.notFound().build();
        zone.setActive(false);
        zoneRepository.save(zone);
        return ResponseEntity.ok(Map.of("deactivated", true));
    }

    @GetMapping("/gate")
    public ResponseEntity<?> getGate() {
        return ResponseEntity.ok(systemConfigService.getGateState());
    }

    @PutMapping("/gate")
    public ResponseEntity<?> setGate(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        Object enabledVal = body.get("enabled");
        if (!(enabledVal instanceof Boolean)) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled must be a boolean"));
        }
        UUID adminId = principal != null ? principal.userId() : null;
        systemConfigService.setZoneGateEnabled((Boolean) enabledVal, adminId);
        return ResponseEntity.ok(systemConfigService.getGateState());
    }

    @GetMapping("/waitlist")
    public ResponseEntity<?> getWaitlist() {
        var entries = waitlistRepository.findAll();
        if (entries.isEmpty()) return ResponseEntity.ok(List.of());

        var userIds = entries.stream().map(e -> e.getUserId()).toList();
        var userMap = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(u -> u.getId(), u -> u));

        var result = entries.stream().map(e -> {
            var u = userMap.get(e.getUserId());
            var row = new LinkedHashMap<String, Object>();
            row.put("userId", e.getUserId());
            row.put("displayName", u != null ? u.getDisplayName() : null);
            row.put("phoneNumber", u != null ? u.getPhoneNumber() : null);
            row.put("approxLat", e.getApproxLat());
            row.put("approxLng", e.getApproxLng());
            row.put("createdAt", e.getCreatedAt());
            row.put("updatedAt", e.getUpdatedAt());
            return row;
        }).toList();

        return ResponseEntity.ok(result);
    }

    private static final int WAITLIST_PAGE_SIZE = 500;

    private void triggerWaitlistReengagement(ServiceZoneEntity zone) {
        OffsetDateTime now = OffsetDateTime.now();
        int page = 0;
        Page<ZoneWaitlistEntity> batch;
        do {
            batch = waitlistRepository.findAll(PageRequest.of(page++, WAITLIST_PAGE_SIZE));
            for (ZoneWaitlistEntity entry : batch.getContent()) {
                if (entry.getApproxLat() == null || entry.getApproxLng() == null) continue;
                boolean inZone = zoneCheckService.findMatchingZone(
                        List.of(zone), entry.getApproxLat(), entry.getApproxLng()).isPresent();
                if (inZone) {
                    publishZoneAvailableEvent(zone, entry.getUserId(), now);
                }
            }
        } while (batch.hasNext());
    }

    private void publishZoneAvailableEvent(ServiceZoneEntity zone, UUID userId, OffsetDateTime occurredAt) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("eventType", NotificationEventType.ZONE_NOW_AVAILABLE.name());
        payload.put("userId", userId.toString());
        payload.put("zoneId", zone.getId().toString());
        payload.put("zoneName", zone.getName());
        payload.put("occurredAt", occurredAt.toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize zone notification payload", e);
        }

        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setId(eventId);
        outbox.setEventType(NotificationEventType.ZONE_NOW_AVAILABLE.name());
        outbox.setAggregateId(zone.getId());
        outbox.setPayloadJson(json);
        outbox.setStatus(NotificationOutboxStatus.PENDING.name());
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(occurredAt);
        outboxRepository.save(outbox);
    }

    private List<String> validateZoneBody(Map<String, Object> body) {
        List<String> errors = new ArrayList<>();

        String name = getString(body, "name");
        if (name == null || name.isBlank()) errors.add("name is required");
        else if (name.length() > 128) errors.add("name must be at most 128 characters");

        String type = getString(body, "type");
        if (type == null) {
            errors.add("type is required");
            return errors;
        }
        if (!type.equals("CIRCLE") && !type.equals("POLYGON")) {
            errors.add("type must be CIRCLE or POLYGON");
            return errors;
        }

        if ("CIRCLE".equals(type)) {
            if (body.get("centerLat") == null) errors.add("centerLat is required for CIRCLE");
            if (body.get("centerLng") == null) errors.add("centerLng is required for CIRCLE");
            if (body.get("radiusKm") == null) errors.add("radiusKm is required for CIRCLE");
            if (body.get("boundary") != null) errors.add("boundary must not be set for CIRCLE");
        } else {
            String boundary = resolveBoundaryJson(body.get("boundary"));
            if (boundary == null || boundary.isBlank()) {
                errors.add("boundary is required for POLYGON");
            } else {
                try {
                    List<?> vertices = objectMapper.readValue(boundary, new TypeReference<List<?>>() {});
                    if (vertices.size() < 3) errors.add("POLYGON boundary must have at least 3 vertices");
                } catch (Exception e) {
                    errors.add("boundary must be a valid JSON array of {lat, lng} objects");
                }
            }
            if (body.get("centerLat") != null) errors.add("centerLat must not be set for POLYGON");
            if (body.get("centerLng") != null) errors.add("centerLng must not be set for POLYGON");
            if (body.get("radiusKm") != null) errors.add("radiusKm must not be set for POLYGON");
        }

        return errors;
    }

    private ServiceZoneEntity applyZoneFields(ServiceZoneEntity zone, Map<String, Object> body) {
        zone.setName(getString(body, "name"));
        zone.setType(getString(body, "type").toUpperCase());
        if (body.containsKey("active")) {
            zone.setActive(Boolean.TRUE.equals(body.get("active")));
        }

        String type = zone.getType();
        if ("CIRCLE".equals(type)) {
            zone.setCenterLat(getDouble(body, "centerLat"));
            zone.setCenterLng(getDouble(body, "centerLng"));
            zone.setRadiusKm(getDouble(body, "radiusKm"));
            zone.setBoundary(null);
        } else {
            zone.setBoundary(resolveBoundaryJson(body.get("boundary")));
            zone.setCenterLat(null);
            zone.setCenterLng(null);
            zone.setRadiusKm(null);
        }

        return zone;
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val instanceof String s ? s.trim() : null;
    }

    private Double getDouble(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    private String resolveBoundaryJson(Object val) {
        if (val == null) return null;
        if (val instanceof String s) return s;
        try { return objectMapper.writeValueAsString(val); } catch (Exception e) { return null; }
    }
}
