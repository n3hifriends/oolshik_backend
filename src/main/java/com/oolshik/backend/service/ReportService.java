// src/main/java/com/oolshik/backend/service/ReportService.java
package com.oolshik.backend.service;

import com.oolshik.backend.domain.ReportReason;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.ReportEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.dto.ReportDtos.CreateRequest;
import com.oolshik.backend.web.dto.ReportDtos.CreateResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Service
public class ReportService {

    private final UserRepository userRepo;
    private final HelpRequestRepository helpRepo;
    private final ReportEventRepository reportRepo;

    public ReportService(UserRepository userRepo,
                         HelpRequestRepository helpRepo,
                         ReportEventRepository reportRepo) {
        this.userRepo = userRepo;
        this.helpRepo = helpRepo;
        this.reportRepo = reportRepo;
    }

    @Transactional
    public CreateResponse create(String principalPhone, CreateRequest req) {
        // Who is reporting?
        UserEntity reporter = userRepo.findByPhoneNumber(principalPhone)
                .orElseThrow(() -> new EntityNotFoundException("Reporter not found"));

        // Validate context: **exactly one** of taskId or targetUserId
        final UUID helpRequestId   = req.taskId();
        final UUID explicitTarget  = req.targetUserId();

        if (helpRequestId == null && explicitTarget == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either taskId or targetUserId is required");
        }
        if (helpRequestId != null && explicitTarget != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide only one of taskId or targetUserId");
        }

        // If OTHER, details are required
        if (req.reason() == ReportReason.OTHER && (req.text() == null || req.text().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Details are required when reason is OTHER");
        }

        // Resolve target user
        UUID targetUserId;
        if (helpRequestId != null) {
            HelpRequestEntity hr = helpRepo.findById(helpRequestId)
                    .orElseThrow(() -> new EntityNotFoundException("Task not found"));
            targetUserId = hr.getRequesterId(); // inferred from the task
            if (targetUserId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Task has no requester to report");
            }
            // If you *want* to allow reporting the helper instead, add a switch here.
        } else {
            // targetUserId flow
            userRepo.findById(explicitTarget)
                    .orElseThrow(() -> new EntityNotFoundException("Target user not found"));
            targetUserId = explicitTarget;
        }

        // Prevent self-reporting (optional but sensible)
        if (reporter.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot report yourself");
        }

        // Optional: dedupe to avoid spam (uncomment if you add this repo method)
        // boolean exists = reportRepo.existsByReporterUserIdAndTargetUserIdAndHelpRequestIdAndReason(
        //         reporter.getId(), targetUserId, helpRequestId, req.reason());
        // if (exists) {
        //     throw new ResponseStatusException(HttpStatus.CONFLICT, "You already reported this");
        // }

        // Persist
        ReportEventEntity ev = new ReportEventEntity();
        ev.setReporterUserId(reporter.getId());
        ev.setTargetUserId(targetUserId);
        ev.setHelpRequestId(helpRequestId); // may be null in user-report flow
        ev.setReason(req.reason());
        ev.setDetails((req.text() != null && !req.text().isBlank()) ? req.text().trim() : null);

        reportRepo.save(ev);
        return new CreateResponse(ev.getId());
    }
}