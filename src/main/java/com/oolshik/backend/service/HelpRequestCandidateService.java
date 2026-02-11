package com.oolshik.backend.service;

import com.oolshik.backend.config.NotificationProperties;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.notification.CandidateState;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.repo.HelpRequestCandidateRepository;
import com.oolshik.backend.repo.HelpRequestNotificationAudienceRepository;
import com.oolshik.backend.repo.HelperLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class HelpRequestCandidateService {

    private final HelperLocationRepository helperLocationRepository;
    private final HelpRequestCandidateRepository candidateRepository;
    private final HelpRequestNotificationAudienceRepository audienceRepository;
    private final NotificationProperties properties;

    public HelpRequestCandidateService(
            HelperLocationRepository helperLocationRepository,
            HelpRequestCandidateRepository candidateRepository,
            HelpRequestNotificationAudienceRepository audienceRepository,
            NotificationProperties properties
    ) {
        this.helperLocationRepository = helperLocationRepository;
        this.candidateRepository = candidateRepository;
        this.audienceRepository = audienceRepository;
        this.properties = properties;
    }

    @Transactional
    public List<UUID> seedCandidatesForNewRequest(HelpRequestEntity task, OffsetDateTime now) {
        if (task.getLocation() == null) {
            return List.of();
        }
        OffsetDateTime freshness = now.minusMinutes(properties.getActiveWindowMinutes());
        List<UUID> helperIds = helperLocationRepository.findEligibleHelpersForRequest(
                task.getId(),
                (double) task.getRadiusMeters(),
                freshness
        );
        for (UUID helperId : helperIds) {
            candidateRepository.insertIgnore(UUID.randomUUID(), task.getId(), helperId, CandidateState.PENDING.name());
            audienceRepository.insertIgnore(
                    UUID.randomUUID(),
                    task.getId(),
                    helperId,
                    NotificationEventType.TASK_CREATED.name(),
                    task.getRadiusMeters()
            );
        }
        return helperIds;
    }

    @Transactional
    public List<UUID> seedCandidatesForRadiusExpansion(
            HelpRequestEntity task,
            int previousRadius,
            int newRadius,
            OffsetDateTime now
    ) {
        if (task.getLocation() == null) {
            return List.of();
        }
        OffsetDateTime freshness = now.minusMinutes(properties.getActiveWindowMinutes());
        List<UUID> helperIds = helperLocationRepository.findNewlyEligibleHelpersForRequest(
                task.getId(),
                (double) newRadius,
                (double) previousRadius,
                freshness
        );
        for (UUID helperId : helperIds) {
            candidateRepository.insertIgnore(UUID.randomUUID(), task.getId(), helperId, CandidateState.PENDING.name());
            audienceRepository.insertIgnore(
                    UUID.randomUUID(),
                    task.getId(),
                    helperId,
                    NotificationEventType.TASK_RADIUS_EXPANDED.name(),
                    newRadius
            );
        }
        return helperIds;
    }
}
