package com.oolshik.backend.service;

import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.HelpRequestRow;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.HelpRequestController;
import com.oolshik.backend.web.error.ForbiddenOperationException;
import com.oolshik.backend.web.error.ConflictOperationException;
import org.apache.coyote.BadRequestException;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    public HelpRequestEntity create(UUID requesterId, String title, String description, int radiusMeters, String voiceUrl, Point location) {
        UserEntity requester = userRepo.findById(requesterId).orElseThrow(() -> new IllegalArgumentException("Requester not found"));
        HelpRequestEntity e = new HelpRequestEntity();
        e.setRequesterId(requester.getId());
        e.setTitle(title);
        e.setDescription(description);
        e.setRadiusMeters(radiusMeters);
        e.setStatus(HelpRequestStatus.OPEN);
        e.setVoiceUrl(voiceUrl);
        e.setLocation(location);
        return repo.save(e);
    }

    public Page<HelpRequestRow> nearby(
            double lat, double lng, int radiusMeters,
            List<String> statuses, Pageable pageable
    ) {

        String statusesCsv = (statuses == null || statuses.isEmpty())
                ? ""  // value ignored when statusesEmpty = true
                : String.join(",", statuses);
        // call repo:
        return repo.findNearbyPaged(lat, lng, radiusMeters, statusesCsv, pageable);
    }

    public HelpRequestRow findTaskByTaskId(
            UUID taskId
    ) {
        return repo.findTaskByTaskId(taskId);
    }


    @Transactional
    public HelpRequestEntity accept(UUID requestId, UUID helperId) {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (e.getStatus() != HelpRequestStatus.OPEN) {
            throw new ConflictOperationException("Request not open"); // 409
        }
        e.setStatus(HelpRequestStatus.ASSIGNED);
        e.setHelperId(helperId);
        return repo.save(e);
    }

    @Transactional
    public HelpRequestEntity complete(UUID requestId, UUID requesterId, HelpRequestController.CompletePayload completePayload) throws BadRequestException {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(e.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can complete"); // -> 403
        }
        e.setStatus(HelpRequestStatus.COMPLETED);
        e.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (completePayload != null && completePayload.rating != null) {
            applyRating(e, requesterId, completePayload.rating, completePayload.feedback);
        }
        return repo.save(e);
    }


    @Transactional
    public HelpRequestEntity rate(UUID requestId, UUID requesterId, HelpRequestController.RatePayload ratePayload) throws BadRequestException {

        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(e.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can complete"); // -> 403
        }
        e.setStatus(HelpRequestStatus.COMPLETED);
        e.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (ratePayload != null && ratePayload.rating != null) {
            applyRating(e, requesterId, ratePayload.rating, ratePayload.feedback);
        }
        return repo.save(e);
    }

    private void applyRating(HelpRequestEntity hr, UUID actorUserId, BigDecimal rating, String feedback) throws BadRequestException {
        BigDecimal r = rating.setScale(1, RoundingMode.HALF_UP);
        if (r.compareTo(new BigDecimal("0.0")) < 0 || r.compareTo(new BigDecimal("5.0")) > 0)
            throw new BadRequestException("rating must be between 0.0 and 5.0");

        // Optional: only requester can rate helper (or vice versa). Adjust as per your rule.
        // e.g., require actorUserId equals hr.getRequesterId() OR hr.getHelperId()

        // Prevent double-rating
        if (hr.getRatingValue() != null)
            throw new BadRequestException("rating already submitted");

        hr.setRatingValue(r);
        hr.setRatedByUserId(actorUserId);
        hr.setRatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // TODO: store feedback somewhere if/when required (another column/table)
    }

    @Transactional
    public HelpRequestEntity cancel(UUID requestId, UUID requesterId) {
        HelpRequestEntity e = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!requesterId.equals(e.getRequesterId())) {
            throw new ForbiddenOperationException("Only requester can cancel"); // -> 403
        }
        e.setStatus(HelpRequestStatus.CANCELLED);
        return repo.save(e);
    }
}
