package com.oolshik.backend.web;

import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
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
    private final UserRepository userRepository;

    public PhoneRevealController(PhoneRevealService phoneRevealService, UserRepository userRepository) {
        this.phoneRevealService = phoneRevealService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{id}/revealPhone")
    public ResponseEntity<RevealPhoneResponse> revealPhone(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") UUID helpRequestId) {

        // principal.getUsername() is the login (your project logs show it is phone)
        var viewer = userRepository.findByPhoneNumber(principal.phone())
                .orElseThrow(() -> new ConflictOperationException("Viewer not found"));

        var body = phoneRevealService.revealPhone(helpRequestId, viewer.getId());
        return ResponseEntity.ok(body);
    }
}