package com.oolshik.backend.service;

import com.oolshik.backend.config.RadiusExpansionProperties;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.repo.HelpRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
public class HelpRequestRadiusExpansionScheduler {

    private static final Logger log = LoggerFactory.getLogger(HelpRequestRadiusExpansionScheduler.class);

    private final HelpRequestRepository requestRepo;
    private final HelpRequestRadiusExpansionService expansionService;
    private final RadiusExpansionProperties properties;

    public HelpRequestRadiusExpansionScheduler(
            HelpRequestRepository requestRepo,
            HelpRequestRadiusExpansionService expansionService,
            RadiusExpansionProperties properties
    ) {
        this.requestRepo = requestRepo;
        this.expansionService = expansionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "#{${app.radius-expansion.jobIntervalSeconds:30} * 1000}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> candidates = requestRepo.findRadiusEscalationCandidates(
                HelpRequestStatus.OPEN,
                now,
                org.springframework.data.domain.PageRequest.of(0, properties.getBatchSize())
        );
        if (candidates.isEmpty()) {
            return;
        }
        int expanded = 0;
        for (UUID requestId : candidates) {
            expansionService.expandRadiusIfDue(requestId);
            expanded++;
        }
        if (expanded > 0) {
            log.info("Radius expansion processed {} candidates", expanded);
        }
    }
}
