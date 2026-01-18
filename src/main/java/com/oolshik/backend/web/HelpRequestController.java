package com.oolshik.backend.web;

import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.media.AudioFileRepository;
import com.oolshik.backend.repo.HelpRequestRow;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.service.HelpRequestService;
import com.oolshik.backend.transcription.TranscriptionJobEntity;
import com.oolshik.backend.transcription.TranscriptionJobPublisher;
import com.oolshik.backend.transcription.TranscriptionJobService;
import com.oolshik.backend.web.dto.AcceptHelpRequestReq;
import com.oolshik.backend.web.dto.HelpRequestDtos.CreateRequest;
import com.oolshik.backend.web.dto.HelpRequestDtos.HelpRequestView;
import com.oolshik.backend.web.dto.HelpRequestDtos.CancelRequest;
import com.oolshik.backend.web.dto.HelpRequestDtos.ReleaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.coyote.BadRequestException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class HelpRequestController {

    private static final Logger log = LoggerFactory.getLogger(HelpRequestController.class);

    private final HelpRequestService service;
    private final UserRepository userRepo;
    private final AudioFileRepository audioRepo; // NEW
    private final TranscriptionJobService transcriptionJobService;
    private final TranscriptionJobPublisher transcriptionJobPublisher;

    private static final String TRANSCRIPTION_ENGINE = "faster-whisper";
    private static final String TRANSCRIPTION_MODEL_VERSION = "unknown";

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326); // SRID 4326 = WGS84

    public static Point toPoint(double lat, double lon) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }
    public HelpRequestController(HelpRequestService service,
                                 UserRepository userRepo,
                                 AudioFileRepository audioRepo,
                                 TranscriptionJobService transcriptionJobService,
                                 TranscriptionJobPublisher transcriptionJobPublisher) {
        this.service = service;
        this.userRepo = userRepo;
        this.audioRepo = audioRepo;
        this.transcriptionJobService = transcriptionJobService;
        this.transcriptionJobPublisher = transcriptionJobPublisher;
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal, @RequestBody @Valid CreateRequest req) {
        var requester = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        Point point = toPoint(req.latitude(), req.longitude()); // 4326
        // HelpRequestEntity created = service.create(
        //         requester.getId(),
        //         req.title(), req.description(),
        //         req.radiusMeters(),
        //         req.voiceUrl(), point
        // );
        final String demoUrl = "https://github.com/voxserv/audio_quality_testing_samples/raw/refs/heads/master/mono_44100/127389__acclivity__thetimehascome.wav";
        HelpRequestEntity created = service.create(
                requester.getId(),
                req.title(), req.description(),
                req.radiusMeters(),
                demoUrl, point
        );
        TranscriptionJobEntity job = null;
        if (created.getVoiceUrl() != null && !created.getVoiceUrl().isBlank()) {
            job = transcriptionJobService.createOrGet(
                    created.getId(),
                    created.getVoiceUrl(),
                    null,
                    TRANSCRIPTION_ENGINE,
                    TRANSCRIPTION_MODEL_VERSION
            );
            try {
                transcriptionJobPublisher.publishJob(job);
            } catch (Exception ex) {
                log.warn("Failed to publish transcription job jobId={} taskId={}: {}",
                        job.getJobId(), job.getTaskId(), ex.toString());
            }
        }
        return ResponseEntity.ok(view(created, job));
    }

    @GetMapping("/nearby")
    public Page<HelpRequestRow> nearby(
        @RequestParam double lat,
        @RequestParam double lng,
        @RequestParam int radiusMeters,
        @RequestParam(required = false) List<String> statuses,
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return service.nearby(lat, lng, radiusMeters, statuses, pageable);
    }

    @GetMapping("/{taskId}")
    public HelpRequestRow findTaskByTaskId(
            @PathVariable UUID taskId
    ) {
        return service.findTaskByTaskId(taskId);
    }


    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(@AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal, @PathVariable UUID id, @RequestBody AcceptHelpRequestReq acceptReq) {
        var helper = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        Point point = toPoint(acceptReq.latitude(), acceptReq.longitude()); // 4326
        var updated = service.accept(id, helper.getId(), point);
        return ResponseEntity.ok(view(updated, null));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable UUID id,
                                                    @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
                                                    @RequestBody(required = false) CompletePayload payload) {
        var requester = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        HelpRequestEntity updated  = null;
        try {
            updated = service.complete(id, requester.getId(), payload);
        } catch (BadRequestException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok(view(updated, null));
    }

    @PostMapping("/{id}/rate")
    public ResponseEntity<?> rate(@PathVariable UUID id,
                                               @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
                                               @RequestBody RatePayload body) {
        var requester = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        HelpRequestEntity updated = null;
        try {
            updated = service.rate(id, requester.getId(), body);
        } catch (BadRequestException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok(view(updated, null));
    }


    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid CancelRequest body
    ) {
        var requester = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        var updated = service.cancel(id, requester.getId(), body);
        return ResponseEntity.ok(view(updated, null));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<?> release(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ReleaseRequest body
    ) {
        var helper = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        var updated = service.release(id, helper.getId(), body);
        return ResponseEntity.ok(view(updated, null));
    }

    @PostMapping("/{id}/reassign")
    public ResponseEntity<?> reassign(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable UUID id
    ) {
        var requester = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        var updated = service.reassign(id, requester.getId());
        return ResponseEntity.ok(view(updated, null));
    }

    private HelpRequestView view(HelpRequestEntity e, TranscriptionJobEntity job) {
        String url = audioRepo
                .findFirstByRequestIdOrderByCreatedAtDesc(e.getId().toString())
                .map(a -> "/api/media/audio/" + a.getId() + "/stream")
                .orElse(e.getVoiceUrl());

        return new HelpRequestView(
                e.getId(), e.getTitle(), e.getDescription(),
                e.getRadiusMeters(), e.getStatus(),
                e.getRequesterId(), e.getHelperId(),
                e.getCreatedAt(),
                url, // NEW
                e.getRatingValue(),
                job == null ? null : job.getJobId(),
                e.getHelperAcceptedAt(),
                e.getAssignmentExpiresAt(),
                e.getCancelledAt(),
                e.getCancelledBy(),
                e.getReassignedCount(),
                e.getReleasedCount()
        );
    }

    // payloads
    public static class CompletePayload { public BigDecimal rating; public String feedback; }
    public static class RatePayload { @NotNull
    public BigDecimal rating; public String feedback; }
}
