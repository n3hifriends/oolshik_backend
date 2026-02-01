package com.oolshik.backend.service;

import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.entity.HelpRequestRatingEntity;
import com.oolshik.backend.repo.HelpRequestRatingRepository;
import org.apache.coyote.BadRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class HelpRequestRatingService {

    private static final BigDecimal MIN_RATING = new BigDecimal("0.0");
    private static final BigDecimal MAX_RATING = new BigDecimal("5.0");

    private final HelpRequestRatingRepository repo;

    public HelpRequestRatingService(HelpRequestRatingRepository repo) {
        this.repo = repo;
    }

    public record RatingSummary(
            BigDecimal ratingByRequester,
            BigDecimal ratingByHelper,
            BigDecimal requesterAvgRating,
            BigDecimal helperAvgRating
    ) {}

    @Transactional(readOnly = true)
    public RatingSummary summaryForRequest(
            UUID requestId,
            UUID requesterId,
            UUID helperId,
            UUID pendingHelperId
    ) {
        BigDecimal ratingByRequester = requesterId == null
                ? null
                : repo.findRatingForRequestAndRater(requestId, requesterId);
        BigDecimal ratingByHelper = helperId == null
                ? null
                : repo.findRatingForRequestAndRater(requestId, helperId);
        UUID helperTargetId = helperId != null ? helperId : pendingHelperId;
        BigDecimal helperAvg = helperTargetId == null ? null : repo.findAvgRatingForUser(helperTargetId);
        BigDecimal requesterAvg = requesterId == null ? null : repo.findAvgRatingForUser(requesterId);
        return new RatingSummary(ratingByRequester, ratingByHelper, requesterAvg, helperAvg);
    }

    @Transactional
    public HelpRequestRatingEntity createRating(
            UUID requestId,
            UUID raterUserId,
            UUID targetUserId,
            HelpRequestActorRole raterRole,
            BigDecimal rating
    ) throws BadRequestException {
        if (requestId == null || raterUserId == null || targetUserId == null) {
            throw new BadRequestException("rating target missing");
        }
        if (raterUserId.equals(targetUserId)) {
            throw new BadRequestException("cannot rate yourself");
        }
        if (rating == null) {
            throw new BadRequestException("rating is required");
        }
        BigDecimal normalized = rating.setScale(1, RoundingMode.HALF_UP);
        if (normalized.compareTo(MIN_RATING) < 0 || normalized.compareTo(MAX_RATING) > 0) {
            throw new BadRequestException("rating must be between 0.0 and 5.0");
        }
        if (repo.existsByRequestIdAndRaterUserId(requestId, raterUserId)) {
            throw new BadRequestException("rating already submitted");
        }

        HelpRequestRatingEntity e = new HelpRequestRatingEntity();
        e.setRequestId(requestId);
        e.setRaterUserId(raterUserId);
        e.setTargetUserId(targetUserId);
        e.setRaterRole(raterRole);
        e.setRatingValue(normalized);

        try {
            return repo.save(e);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("rating already submitted");
        }
    }
}
