package com.oolshik.backend.web;

import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.media.AudioFileRepository;
import com.oolshik.backend.repo.HelpRequestRow;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.service.HelpRequestService;
import com.oolshik.backend.web.dto.HelpRequestDtos.CreateRequest;
import com.oolshik.backend.web.dto.HelpRequestDtos.HelpRequestView;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class HelpRequestController {

    private final HelpRequestService service;
    private final UserRepository userRepo;
    private final AudioFileRepository audioRepo; // NEW

    public HelpRequestController(HelpRequestService service, UserRepository userRepo, AudioFileRepository audioRepo) {
        this.service = service;
        this.userRepo = userRepo;
        this.audioRepo = audioRepo;
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal User principal, @RequestBody @Valid CreateRequest req) {
        var requester = userRepo.findByPhoneNumber(principal.getUsername()).orElseThrow();
        HelpRequestEntity created = service.create(
                requester.getId(),
                req.title(), req.description(),
                req.latitude(), req.longitude(),
                req.radiusMeters(),
                req.voiceUrl()
        );
        return ResponseEntity.ok(view(created));
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

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(@AuthenticationPrincipal User principal, @PathVariable UUID id) {
        var helper = userRepo.findByPhoneNumber(principal.getUsername()).orElseThrow();
        var updated = service.accept(id, helper.getId());
        return ResponseEntity.ok(view(updated));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(@AuthenticationPrincipal User principal, @PathVariable UUID id) {
        var requester = userRepo.findByPhoneNumber(principal.getUsername()).orElseThrow();
        var updated = service.complete(id, requester.getId());
        return ResponseEntity.ok(view(updated));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal User principal, @PathVariable UUID id) {
        var requester = userRepo.findByPhoneNumber(principal.getUsername()).orElseThrow();
        var updated = service.cancel(id, requester.getId());
        return ResponseEntity.ok(view(updated));
    }

    private HelpRequestView view(HelpRequestEntity e) {
        String url = audioRepo
                .findFirstByRequestIdOrderByCreatedAtDesc(e.getId().toString())
                .map(a -> "/api/media/audio/" + a.getId() + "/stream")
                .orElse(null);

        return new HelpRequestView(
                e.getId(), e.getTitle(), e.getDescription(),
                e.getLatitude(), e.getLongitude(),
                e.getRadiusMeters(), e.getStatus(),
                e.getRequesterId(), e.getHelperId(),
                e.getCreatedAt(),
                url // NEW
        );
    }
}
