package com.oolshik.backend.service;

import com.oolshik.backend.domain.FeedbackContextType;
import com.oolshik.backend.domain.FeedbackType;
import com.oolshik.backend.entity.FeedbackEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.FeedbackEventRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.web.dto.FeedbackDtos.CreateRequest;
import com.oolshik.backend.web.dto.FeedbackDtos.CreateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackEventRepository feedbackRepo;
    private final UserService userService;
    private final int maxPerDay;
    private final int rateLimitWindowHours;
    private final int retentionDays;

    public FeedbackService(
            FeedbackEventRepository feedbackRepo,
            UserService userService,
            @Value("${app.feedback.maxPerDay:10}") int maxPerDay,
            @Value("${app.feedback.rateLimitWindowHours:24}") int rateLimitWindowHours,
            @Value("${app.feedback.retentionDays:365}") int retentionDays
    ) {
        this.feedbackRepo = feedbackRepo;
        this.userService = userService;
        this.maxPerDay = maxPerDay;
        this.rateLimitWindowHours = rateLimitWindowHours;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public CreateResponse create(
            FirebaseTokenFilter.FirebaseUserPrincipal principal,
            String idempotencyKey,
            CreateRequest req
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }

        if (req.feedbackType() == FeedbackType.CSAT && req.rating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating is required for CSAT");
        }

        if (req.contextType() == FeedbackContextType.TASK && req.contextId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contextId is required for TASK feedback");
        }

        UserEntity user = userService.getOrCreate(principal, null, null);
        UUID userId = user.getId();

        Optional<FeedbackEventEntity> existing = feedbackRepo.findByUserIdAndIdempotencyKey(userId, idempotencyKey.trim());
        if (existing.isPresent()) {
            FeedbackEventEntity ev = existing.get();
            return new CreateResponse(ev.getId(), ev.getCreatedAt());
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime since = now.minusHours(rateLimitWindowHours);
        long count = feedbackRepo.countByUserIdAndCreatedAtAfter(userId, since);
        if (count >= maxPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }

        FeedbackEventEntity ev = new FeedbackEventEntity();
        ev.setUserId(userId);
        ev.setFeedbackType(req.feedbackType());
        ev.setContextType(req.contextType());
        ev.setContextId(req.contextId());
        if (req.rating() != null) {
            ev.setRating(req.rating().shortValue());
        }
        ev.setMessage(trimToNull(req.message()));
        ev.setLocale(trimToNull(req.locale()));
        ev.setAppVersion(trimToNull(req.appVersion()));
        ev.setOs(trimToNull(req.os()));
        ev.setDeviceModel(trimToNull(req.deviceModel()));
        ev.setIdempotencyKey(idempotencyKey.trim());
        ev.setCreatedAt(now);
        ev.setRetentionUntil(now.plusDays(retentionDays));

        List<String> cleanedTags = req.tags() == null ? null : req.tags().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (cleanedTags != null && !cleanedTags.isEmpty()) {
            ev.setTags(cleanedTags);
        }

        feedbackRepo.save(ev);
        log.info("feedback.created userId={} type={} context={} id={}", userId, req.feedbackType(), req.contextType(), ev.getId());
        return new CreateResponse(ev.getId(), ev.getCreatedAt());
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
