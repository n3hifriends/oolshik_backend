package com.oolshik.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.backend.entity.ServiceZoneEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ZoneCheckService {

    private static final Logger log = LoggerFactory.getLogger(ZoneCheckService.class);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final ObjectMapper objectMapper;

    public ZoneCheckService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<ServiceZoneEntity> findMatchingZone(List<ServiceZoneEntity> activeZones, double lat, double lng) {
        for (ServiceZoneEntity zone : activeZones) {
            try {
                if ("CIRCLE".equalsIgnoreCase(zone.getType())) {
                    if (isInCircle(zone, lat, lng)) return Optional.of(zone);
                } else if ("POLYGON".equalsIgnoreCase(zone.getType())) {
                    if (isInPolygon(zone, lat, lng)) return Optional.of(zone);
                }
            } catch (Exception e) {
                log.error("Zone {} skipped due to config error — fix the zone boundary data: {}", zone.getId(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    private boolean isInCircle(ServiceZoneEntity zone, double lat, double lng) {
        if (zone.getCenterLat() == null || zone.getCenterLng() == null || zone.getRadiusKm() == null) return false;
        double distance = haversineKm(lat, lng, zone.getCenterLat(), zone.getCenterLng());
        return distance <= zone.getRadiusKm();
    }

    private boolean isInPolygon(ServiceZoneEntity zone, double lat, double lng) {
        if (zone.getBoundary() == null || zone.getBoundary().isBlank()) return false;
        try {
            List<Map<String, Double>> vertices = objectMapper.readValue(
                    zone.getBoundary(), new TypeReference<>() {});
            return rayCast(vertices, lat, lng);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed boundary JSON for zone " + zone.getId(), e);
        }
    }

    // Ray-casting algorithm — point-in-polygon test
    private boolean rayCast(List<Map<String, Double>> vertices, double lat, double lng) {
        int n = vertices.size();
        if (n < 3) return false;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double vi_lat = vertices.get(i).get("lat");
            double vi_lng = vertices.get(i).get("lng");
            double vj_lat = vertices.get(j).get("lat");
            double vj_lng = vertices.get(j).get("lng");
            boolean crosses = ((vi_lng > lng) != (vj_lng > lng))
                    && (lat < (vj_lat - vi_lat) * (lng - vi_lng) / (vj_lng - vi_lng) + vi_lat);
            if (crosses) inside = !inside;
        }
        return inside;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
