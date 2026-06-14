package com.oolshik.backend.web;

import com.oolshik.backend.admin.AdminDtos.PageResponse;
import com.oolshik.backend.admin.AdminNotificationDtos.UnreadCountResponse;
import com.oolshik.backend.admin.AdminNotificationDtos.UserNotificationResponse;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.service.UserNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class UserNotificationController {

    private final UserNotificationService service;

    public UserNotificationController(UserNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UserNotificationResponse> getInbox(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireAuth(principal);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        if (size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        Page<UserNotificationResponse> result = service.getInbox(principal.userId(), PageRequest.of(page, size));
        return PageResponse.from(result);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        requireAuth(principal);
        return service.getUnreadCount(principal.userId());
    }

    @PatchMapping("/{id}/read")
    public UserNotificationResponse markRead(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.markRead(principal.userId(), id);
    }

    @PatchMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        requireAuth(principal);
        service.markAllRead(principal.userId());
    }

    private void requireAuth(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
