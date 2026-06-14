package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminNotificationDtos.BroadcastDetail;
import com.oolshik.backend.admin.AdminNotificationDtos.BroadcastSummary;
import com.oolshik.backend.admin.AdminNotificationDtos.SendBroadcastRequest;
import com.oolshik.backend.admin.AdminNotificationDtos.SendBroadcastResponse;
import com.oolshik.backend.config.AdminNotificationProperties;
import com.oolshik.backend.entity.AdminBroadcastEntity;
import com.oolshik.backend.repo.AdminBroadcastRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminNotificationService {

    private final AdminBroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final AdminNotificationTemplateService templateService;
    private final AdminNotificationProperties props;

    public AdminNotificationService(
            AdminBroadcastRepository broadcastRepository,
            UserRepository userRepository,
            HelpRequestRepository helpRequestRepository,
            AdminNotificationTemplateService templateService,
            AdminNotificationProperties props
    ) {
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.templateService = templateService;
        this.props = props;
    }

    @Transactional
    public SendBroadcastResponse queueBroadcast(SendBroadcastRequest request, UUID createdBy) {
        validateRequest(request);
        List<String> channels = normalizeChannels(request.channels());

        boolean smsRequested = channels.contains("SMS");
        if (smsRequested && !props.getSms().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SMS channel is not configured");
        }
        if (smsRequested && !hasUsableSmsConfig()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SMS provider credentials are not configured");
        }

        long estimated = estimateRecipients(request.targetType(), request.targetValue());
        if (estimated > props.getMaxRecipients()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estimated recipients (" + estimated + ") exceeds max allowed (" + props.getMaxRecipients() + ")");
        }

        UUID savedTemplateId = request.templateId();
        if (request.saveAsTemplate()) {
            if (request.templateName() == null || request.templateName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "templateName is required when saveAsTemplate is true");
            }
            var template = templateService.saveAsTemplate(request.templateName(), request.title(), request.body(), createdBy);
            savedTemplateId = template.getId();
        }

        String resolvedRouteKey = resolveRouteKey(request.routeKey());
        String resolvedRouteTargetId = resolveRouteTargetId(resolvedRouteKey, request.routeTargetId());

        AdminBroadcastEntity entity = new AdminBroadcastEntity();
        entity.setTemplateId(savedTemplateId);
        entity.setTitle(request.title());
        entity.setBody(request.body());
        entity.setTargetType(request.targetType().toUpperCase());
        entity.setTargetValue(request.targetValue());
        entity.setChannels(String.join(",", channels));
        entity.setStatus("QUEUED");
        entity.setCreatedBy(createdBy);
        entity.setRouteKey(resolvedRouteKey);
        entity.setRouteTargetId(resolvedRouteTargetId);
        entity = broadcastRepository.save(entity);

        return new SendBroadcastResponse(entity.getId(), entity.getStatus(), (int) estimated);
    }

    @Transactional(readOnly = true)
    public Page<BroadcastSummary> listBroadcasts(Pageable pageable) {
        return broadcastRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public BroadcastDetail getBroadcast(UUID id) {
        return broadcastRepository.findById(id)
                .map(this::toDetail)
                .orElseThrow(() -> new EntityNotFoundException("Broadcast not found: " + id));
    }

    public long estimateRecipients(String targetType, String targetValue) {
        return switch (targetType.toUpperCase()) {
            case "ALL" -> userRepository.count();
            case "ROLE" -> userRepository.countByRolesContaining(targetValue);
            case "USER" -> 1;
            case "REQUEST" -> {
                UUID requestId = UUID.fromString(targetValue);
                var req = helpRequestRepository.findById(requestId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request not found: " + targetValue));
                yield req.getHelperId() != null ? 2 : 1;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid targetType: " + targetType);
        };
    }

    public List<UUID> resolveRecipientIds(String targetType, String targetValue) {
        return switch (targetType.toUpperCase()) {
            case "ALL" -> userRepository.findAll().stream().map(u -> u.getId()).toList();
            case "ROLE" -> userRepository.findForAdminByRole(targetValue, Pageable.unpaged())
                    .stream().map(u -> u.getId()).toList();
            case "USER" -> List.of(UUID.fromString(targetValue));
            case "REQUEST" -> {
                UUID requestId = UUID.fromString(targetValue);
                var req = helpRequestRepository.findById(requestId)
                        .orElseThrow(() -> new EntityNotFoundException("Request not found: " + targetValue));
                yield req.getHelperId() != null
                        ? List.of(req.getRequesterId(), req.getHelperId())
                        : List.of(req.getRequesterId());
            }
            default -> throw new IllegalArgumentException("Invalid targetType: " + targetType);
        };
    }

    private static final java.util.Set<String> ALLOWED_ROUTE_KEYS =
            java.util.Set.of("InAppInbox", "AdminBroadcast", "TaskDetail");

    private String resolveRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return "InAppInbox";
        if (!ALLOWED_ROUTE_KEYS.contains(routeKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid routeKey: " + routeKey);
        }
        return routeKey;
    }

    private String resolveRouteTargetId(String routeKey, String routeTargetId) {
        if ("TaskDetail".equals(routeKey)) {
            if (routeTargetId == null || routeTargetId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "routeTargetId (task UUID) is required when routeKey is TaskDetail");
            }
            try {
                UUID.fromString(routeTargetId.trim());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "routeTargetId must be a valid UUID for routeKey TaskDetail");
            }
            return routeTargetId.trim();
        }
        return null;
    }

    private void validateRequest(SendBroadcastRequest request) {
        String tt = request.targetType();
        if (tt == null || tt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetType is required");
        }
        if (!List.of("ALL", "ROLE", "USER", "REQUEST").contains(tt.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid targetType: " + tt);
        }
        if (!"ALL".equalsIgnoreCase(tt) && (request.targetValue() == null || request.targetValue().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetValue is required for targetType " + tt);
        }
        if (request.channels() == null || request.channels().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one channel is required");
        }
        normalizeChannels(request.channels());
    }

    private List<String> normalizeChannels(List<String> channels) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String channel : channels) {
            if (channel == null || channel.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel must not be blank");
            }
            String value = channel.trim().toUpperCase();
            if (!List.of("IN_APP", "PUSH", "SMS").contains(value)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid channel: " + channel);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private boolean hasUsableSmsConfig() {
        AdminNotificationProperties.Sms sms = props.getSms();
        return StringUtils.hasText(sms.getMsg91ApiKey())
                && StringUtils.hasText(sms.getMsg91SenderId())
                && StringUtils.hasText(sms.getMsg91BaseUrl());
    }

    private BroadcastSummary toSummary(AdminBroadcastEntity e) {
        return new BroadcastSummary(
                e.getId(), e.getTargetType(), e.getTargetValue(), e.getChannels(), e.getStatus(),
                e.getTotalRecipients(), e.getPushSent(), e.getPushFailed(),
                e.getSmsSent(), e.getSmsFailed(), e.getInAppCreated(),
                e.getCreatedAt(), e.getCompletedAt()
        );
    }

    private BroadcastDetail toDetail(AdminBroadcastEntity e) {
        return new BroadcastDetail(
                e.getId(), e.getTitle(), e.getBody(), e.getTargetType(), e.getTargetValue(),
                e.getChannels(), e.getStatus(), e.getTotalRecipients(),
                e.getPushSent(), e.getPushFailed(), e.getSmsSent(), e.getSmsFailed(), e.getInAppCreated(),
                e.getCreatedBy(), e.getCreatedAt(), e.getProcessingStartedAt(), e.getCompletedAt()
        );
    }
}
