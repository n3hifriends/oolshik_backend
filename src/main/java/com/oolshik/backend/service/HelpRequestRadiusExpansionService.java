package com.oolshik.backend.service;

import com.oolshik.backend.config.RadiusExpansionProperties;
import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.notification.AssignmentChange;
import com.oolshik.backend.notification.NotificationEventContext;
import com.oolshik.backend.notification.NotificationEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HelpRequestRadiusExpansionService {

    private static final Logger log = LoggerFactory.getLogger(HelpRequestRadiusExpansionService.class);

    private final RadiusExpansionProperties properties;
    private final HelpRequestRepository requestRepo;
    private final HelpRequestNotificationService notificationService;
    private final HelpRequestEventService eventService;
    private final HelpRequestCandidateService candidateService;

    public HelpRequestRadiusExpansionService(
            RadiusExpansionProperties properties,
            HelpRequestRepository requestRepo,
            HelpRequestNotificationService notificationService,
            HelpRequestEventService eventService,
            HelpRequestCandidateService candidateService
    ) {
        this.properties = properties;
        this.requestRepo = requestRepo;
        this.notificationService = notificationService;
        this.eventService = eventService;
        this.candidateService = candidateService;
    }

    public OffsetDateTime initialNextEscalationAt(OffsetDateTime now, int currentRadius) {
        Optional<Integer> nextRadius = findNextRadius(currentRadius);
        if (nextRadius.isEmpty()) return null;
        return now.plusSeconds(delayForStage(1));
    }

    public OffsetDateTime nextEscalationAtForStage(OffsetDateTime now, int nextStage) {
        return now.plusSeconds(delayForStage(nextStage));
    }

    public Optional<Integer> findNextRadius(int currentRadius) {
        List<Integer> schedule = normalizedSchedule();
        return schedule.stream().filter(r -> r > currentRadius).findFirst();
    }

    private List<Integer> normalizedSchedule() {
        List<Integer> schedule = new java.util.ArrayList<>(properties.getScheduleMeters());
        schedule.sort(Comparator.naturalOrder());
        int max = properties.getMaxRadiusMeters();
        if (max > 0) {
            schedule.removeIf(r -> r > max);
        }
        return schedule;
    }

    private int delayForStage(int stageIndex) {
        List<Integer> delays = properties.getEscalationDelaysSeconds();
        if (delays == null || delays.isEmpty()) return 0;
        int idx = Math.min(stageIndex, delays.size() - 1);
        return Math.max(0, delays.get(idx));
    }

    @Transactional
    public void expandRadiusIfDue(UUID requestId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        HelpRequestEntity task = requestRepo.findById(requestId).orElse(null);
        if (task == null) return;
        if (task.getStatus() != HelpRequestStatus.OPEN) return;
        if (task.getNextEscalationAt() == null || task.getNextEscalationAt().isAfter(now)) return;

        int currentStage = task.getRadiusStage() == null ? 0 : task.getRadiusStage();
        int currentRadius = task.getRadiusMeters();

        Optional<Integer> nextRadiusOpt = findNextRadius(currentRadius);
        if (nextRadiusOpt.isEmpty()) {
            requestRepo.stopRadiusEscalation(
                    requestId,
                    now,
                    HelpRequestStatus.OPEN,
                    HelpRequestEventType.RADIUS_EXPANDED.name()
            );
            return;
        }

        int nextRadius = nextRadiusOpt.get();
        int nextStage = currentStage + 1;
        OffsetDateTime nextEscalationAt = nextEscalationAtForStage(now, nextStage + 1);
        int updated = requestRepo.updateRadiusEscalation(
                requestId,
                now,
                currentStage,
                nextStage,
                nextRadius,
                nextEscalationAt,
                HelpRequestStatus.OPEN,
                HelpRequestEventType.RADIUS_EXPANDED.name()
        );
        if (updated == 0) {
            return;
        }

        eventService.record(
                requestId,
                HelpRequestEventType.RADIUS_EXPANDED,
                HelpRequestActorRole.SYSTEM,
                null,
                null,
                null,
                String.format("{\"oldRadius\":%d,\"newRadius\":%d,\"wave\":%d}", currentRadius, nextRadius, nextStage)
        );

        candidateService.seedCandidatesForRadiusExpansion(task, currentRadius, nextRadius, now);
        NotificationEventContext context = new NotificationEventContext();
        context.setActorUserId(null);
        context.setPreviousStatus(HelpRequestStatus.OPEN.name());
        context.setNewStatus(HelpRequestStatus.OPEN.name());
        context.setAssignmentChange(AssignmentChange.NONE);
        context.setPreviousHelperId(task.getHelperId());
        context.setNewHelperId(task.getHelperId());
        context.setPreviousRadiusMeters(currentRadius);
        context.setNewRadiusMeters(nextRadius);
        context.setOccurredAt(now);
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_RADIUS_EXPANDED, task, context);
    }
}
