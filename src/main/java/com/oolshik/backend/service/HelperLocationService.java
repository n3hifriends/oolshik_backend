package com.oolshik.backend.service;

import com.oolshik.backend.entity.HelperLocationEntity;
import com.oolshik.backend.repo.HelperLocationRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class HelperLocationService {

    private final HelperLocationRepository repo;

    public HelperLocationService(HelperLocationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void upsert(UUID helperId, Point location) {
        HelperLocationEntity entity = repo.findById(helperId).orElseGet(HelperLocationEntity::new);
        entity.setHelperId(helperId);
        entity.setLocation(location);
        entity.setLastSeenAt(OffsetDateTime.now());
        repo.save(entity);
    }
}
