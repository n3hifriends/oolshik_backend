// src/main/java/com/oolshik/backend/service/ReportService.java
package com.oolshik.backend.service;

import com.oolshik.backend.domain.ReportReason;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.ReportEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
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
    public CreateResponse create(FirebaseTokenFilter.FirebaseUserPrincipal principal, CreateRequest req) {
        UserEntity reporter = resolveReporter(principal);

        // Validate context: **exactly one** of taskId or targetUserId
        final UUID helpRequestId   = req.taskId();
        final UUID explicitTarget  = req.targetUserId();

        if (helpRequestId == null && explicitTarget == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "errors.report.taskOrTargetRequired");
        }
        if (helpRequestId != null && explicitTarget != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "errors.report.onlyOneTarget");
        }

        // If OTHER, details are required
        if (req.reason() == ReportReason.OTHER && (req.text() == null || req.text().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "errors.report.detailsRequiredForOther");
        }

        // Resolve target user
        UUID targetUserId;
        if (helpRequestId != null) {
            HelpRequestEntity hr = helpRepo.findById(helpRequestId)
                    .orElseThrow(() -> new EntityNotFoundException("errors.report.taskNotFound"));
            targetUserId = hr.getRequesterId(); // inferred from the task
            if (targetUserId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "errors.report.taskNoRequester");
            }
            // If you *want* to allow reporting the helper instead, add a switch here.
        } else {
            // targetUserId flow
            userRepo.findById(explicitTarget)
                    .orElseThrow(() -> new EntityNotFoundException("errors.report.targetUserNotFound"));
            targetUserId = explicitTarget;
        }

        // Prevent self-reporting (optional but sensible)
        if (reporter.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "errors.report.selfReportForbidden");
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

    private UserEntity resolveReporter(FirebaseTokenFilter.FirebaseUserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "errors.auth.required");
        }
        if (principal.uid() != null && !principal.uid().isBlank()) {
            var byUid = userRepo.findByFirebaseUid(principal.uid());
            if (byUid.isPresent()) {
                return byUid.get();
            }
        }
        if (principal.phone() != null && !principal.phone().isBlank()) {
            return userRepo.findByPhoneNumber(principal.phone())
                    .orElseThrow(() -> new EntityNotFoundException("errors.report.reporterNotFound"));
        }
        throw new EntityNotFoundException("errors.report.reporterNotFound");
    }
}
