package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.model.NotificationEventPayload;
import com.oolshik.notificationworker.model.NotificationEventType;
import com.oolshik.notificationworker.repo.HelpRequestCandidateRepository;
import com.oolshik.notificationworker.repo.HelpRequestNotificationAudienceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RecipientResolver {

    private final HelpRequestCandidateRepository candidateRepository;
    private final HelpRequestNotificationAudienceRepository audienceRepository;

    public RecipientResolver(
            HelpRequestCandidateRepository candidateRepository,
            HelpRequestNotificationAudienceRepository audienceRepository
    ) {
        this.candidateRepository = candidateRepository;
        this.audienceRepository = audienceRepository;
    }

    public List<UUID> resolve(NotificationEventPayload payload) {
        NotificationEventType type = NotificationEventType.valueOf(payload.getEventType());
        Set<UUID> recipients = new LinkedHashSet<>();
        switch (type) {
            case TASK_CREATED, TASK_RADIUS_EXPANDED -> {
                Integer radius = payload.getNewRadiusMeters();
                if (radius == null) {
                    return List.of();
                }
                recipients.addAll(audienceRepository.findAudienceUserIds(
                        payload.getTaskId(),
                        payload.getEventType(),
                        radius
                ));
            }
            case OFFER_UPDATED -> {
                List<UUID> candidateHelpers = candidateRepository.findHelperIdsByRequestIdAndStates(
                        payload.getTaskId(),
                        List.of("PENDING", "NOTIFIED")
                );
                recipients.addAll(candidateHelpers);
            }
            case TASK_AUTH_REQUESTED -> {
                addIfPresent(recipients, payload.getRequesterUserId());
            }
            case TASK_AUTH_APPROVED, TASK_AUTH_REJECTED -> {
                addIfPresent(recipients, helperId(payload));
            }
            case TASK_AUTH_TIMEOUT -> {
                addIfPresent(recipients, payload.getRequesterUserId());
                addIfPresent(recipients, helperId(payload));
            }
            case TASK_CANCELLED -> {
                addIfPresent(recipients, payload.getPreviousHelperId());
                List<UUID> candidateHelpers = candidateRepository.findHelperIdsByRequestIdAndStates(
                        payload.getTaskId(),
                        List.of("PENDING", "NOTIFIED")
                );
                recipients.addAll(candidateHelpers);
            }
            case TASK_RELEASED -> {
                addIfPresent(recipients, payload.getRequesterUserId());
            }
            case TASK_REASSIGNED, TASK_TIMEOUT -> {
                addIfPresent(recipients, payload.getRequesterUserId());
                addIfPresent(recipients, helperId(payload));
            }
            case PAYMENT_REQUEST_CREATED -> {
                addIfPresent(recipients, payload.getRequesterUserId());
                addIfPresent(recipients, helperUserId(payload));
                if (payload.getActorUserId() != null) {
                    recipients.remove(payload.getActorUserId());
                }
            }
            case PAYMENT_ACTION_REQUIRED -> addIfPresent(recipients, payload.getPayerUserId());
            case PAYMENT_INITIATED, PAYMENT_MARKED_PAID -> {
                UUID payer = payload.getPayerUserId();
                UUID requester = payload.getRequesterUserId();
                UUID helper = helperUserId(payload);
                if (payer == null || requester == null || helper == null) {
                    if (requester != null) recipients.add(requester);
                    if (helper != null) recipients.add(helper);
                } else if (payer.equals(requester)) {
                    recipients.add(helper);
                } else {
                    recipients.add(requester);
                }
            }
            case PAYMENT_DISPUTED -> {
                addIfPresent(recipients, payload.getRequesterUserId());
                addIfPresent(recipients, helperUserId(payload));
            }
            case PAYMENT_EXPIRED -> {
                addIfPresent(recipients, payload.getPayerUserId());
                if (payload.getRequesterUserId() != null && !payload.getRequesterUserId().equals(payload.getPayerUserId())) {
                    addIfPresent(recipients, payload.getRequesterUserId());
                }
            }
        }
        return new ArrayList<>(recipients);
    }

    private UUID helperId(NotificationEventPayload payload) {
        return payload.getNewHelperId() != null ? payload.getNewHelperId() : payload.getPreviousHelperId();
    }

    private UUID helperUserId(NotificationEventPayload payload) {
        if (payload.getHelperUserId() != null) {
            return payload.getHelperUserId();
        }
        return helperId(payload);
    }

    private void addIfPresent(Set<UUID> recipients, UUID userId) {
        if (userId != null) {
            recipients.add(userId);
        }
    }
}
