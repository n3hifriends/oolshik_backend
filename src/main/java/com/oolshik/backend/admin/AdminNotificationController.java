package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminDtos.PageResponse;
import com.oolshik.backend.admin.AdminNotificationDtos.BroadcastDetail;
import com.oolshik.backend.admin.AdminNotificationDtos.BroadcastSummary;
import com.oolshik.backend.admin.AdminNotificationDtos.CreateTemplateRequest;
import com.oolshik.backend.admin.AdminNotificationDtos.DeliveryRow;
import com.oolshik.backend.admin.AdminNotificationDtos.SendBroadcastRequest;
import com.oolshik.backend.admin.AdminNotificationDtos.SendBroadcastResponse;
import com.oolshik.backend.admin.AdminNotificationDtos.TemplateResponse;
import com.oolshik.backend.entity.AdminBroadcastDeliveryEntity;
import com.oolshik.backend.repo.AdminBroadcastDeliveryRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminNotificationService notificationService;
    private final AdminNotificationTemplateService templateService;
    private final AdminBroadcastDeliveryRepository deliveryRepository;

    public AdminNotificationController(
            AdminNotificationService notificationService,
            AdminNotificationTemplateService templateService,
            AdminBroadcastDeliveryRepository deliveryRepository
    ) {
        this.notificationService = notificationService;
        this.templateService = templateService;
        this.deliveryRepository = deliveryRepository;
    }

    @PostMapping("/send")
    public ResponseEntity<SendBroadcastResponse> send(
            @Valid @RequestBody SendBroadcastRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        requireAuth(principal);
        SendBroadcastResponse response = notificationService.queueBroadcast(request, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/broadcasts")
    public PageResponse<BroadcastSummary> listBroadcasts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<BroadcastSummary> result = notificationService.listBroadcasts(pageRequest(page, size));
        return PageResponse.from(result);
    }

    @GetMapping("/broadcasts/{id}")
    public BroadcastDetail getBroadcast(@PathVariable UUID id) {
        return notificationService.getBroadcast(id);
    }

    @GetMapping("/broadcasts/{id}/deliveries")
    public PageResponse<DeliveryRow> getDeliveries(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<AdminBroadcastDeliveryEntity> deliveries = deliveryRepository.findByBroadcastId(
                id, pageRequest(page, size, Sort.by(Sort.Direction.DESC, "sentAt")));
        Page<DeliveryRow> rows = deliveries.map(d -> new DeliveryRow(
                d.getId(), d.getUserId(), d.getChannel(), d.getStatus(), d.getError(), d.getSentAt()));
        return PageResponse.from(rows);
    }

    @PostMapping("/templates")
    public ResponseEntity<TemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        requireAuth(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.create(request, principal.userId()));
    }

    @GetMapping("/templates")
    public List<TemplateResponse> listTemplates() {
        return templateService.listAll();
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAuth(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        return pageRequest(page, size, Sort.unsorted());
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        return PageRequest.of(page, size, sort);
    }
}
