package com.oolshik.backend.web;

import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.PhoneRevealService;
import com.oolshik.backend.web.dto.PhoneRevealDtos.RevealPhoneResponse;
import com.oolshik.backend.web.error.ConflictOperationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class PhoneRevealController {

    private final PhoneRevealService phoneRevealService;
    private final CurrentUserService currentUserService;

    public PhoneRevealController(PhoneRevealService phoneRevealService, CurrentUserService currentUserService) {
        this.phoneRevealService = phoneRevealService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{id}/revealPhone")
    public ResponseEntity<RevealPhoneResponse> revealPhone(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable("id") UUID helpRequestId) {

        // principal.getUsername() is the login (your project logs show it is phone)
        var viewer = currentUserService.resolve(principal);
        if (viewer == null) {
            throw new ConflictOperationException("Viewer not found");
        }

        var body = phoneRevealService.revealPhone(helpRequestId, viewer.getId());
        return ResponseEntity.ok(body);
    }
}
