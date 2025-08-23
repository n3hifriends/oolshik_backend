package com.oolshik.backend.service;

import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class HelpRequestService {

    private final HelpRequestRepository repo;
    private final UserRepository userRepo;

    public HelpRequestService(HelpRequestRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    @Transactional
    public HelpRequestEntity create(UUID requesterId, String title, String description, double lat, double lon, int radiusMeters) {
        UserEntity requester = userRepo.findById(requesterId).orElseThrow(() -> new IllegalArgumentException("Requester not found"));
        HelpRequestEntity e = new HelpRequestEntity();
        e.setRequesterId(requester.getId());
        e.setTitle(title);
        e.setDescription(description);
        e.setLatitude(lat);
        e.setLongitude(lon);
        e.setRadiusMeters(radiusMeters);
        e.setStatus(HelpRequestStatus.OPEN);
        return repo.save(e);
    }

    public Page<HelpRequestEntity> nearby(
        double lat, double lng, int radiusMeters,
        List<String> statuses, Pageable pageable
    ) {
        final List<String> normalized = (statuses == null) ? List.of() :
            statuses.stream().filter(s -> s != null && !s.isBlank()).toList();

        final boolean statusesEmpty = normalized.isEmpty();

        return repo.findNearbyPaged(
            lat, lng, radiusMeters,
            statusesEmpty,
            statusesEmpty ? List.of("") : normalized,
            pageable
        );
    }

    @Transactional
    public HelpRequestEntity accept(UUID requestId, UUID helperId) {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (e.getStatus() != HelpRequestStatus.OPEN) throw new IllegalStateException("Request not open");
        e.setStatus(HelpRequestStatus.ASSIGNED);
        e.setHelperId(helperId);
        return repo.save(e);
    }

    @Transactional
    public HelpRequestEntity complete(UUID requestId, UUID requesterId) {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(e.getRequesterId())) throw new IllegalArgumentException("Only requester can complete");
        e.setStatus(HelpRequestStatus.COMPLETED);
        return repo.save(e);
    }

    @Transactional
    public HelpRequestEntity cancel(UUID requestId, UUID requesterId) {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(e.getRequesterId())) throw new IllegalArgumentException("Only requester can cancel");
        e.setStatus(HelpRequestStatus.CANCELLED);
        return repo.save(e);
    }
}
