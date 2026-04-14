package com.oolshik.backend.web;

import com.oolshik.backend.entity.PaymentProfileEntity;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.PaymentProfileService;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileEditResponse;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileMeResponse;
import com.oolshik.backend.web.dto.PaymentProfileDtos.PaymentProfileUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-profile")
public class PaymentProfileController {

    private final CurrentUserService currentUserService;
    private final PaymentProfileService paymentProfileService;

    public PaymentProfileController(
            CurrentUserService currentUserService,
            PaymentProfileService paymentProfileService
    ) {
        this.currentUserService = currentUserService;
        this.paymentProfileService = paymentProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<PaymentProfileMeResponse> me(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        var user = currentUserService.require(principal);
        return ResponseEntity.ok(paymentProfileService.getMyProfile(user.getId()));
    }

    @GetMapping("/me/edit")
    public ResponseEntity<PaymentProfileEditResponse> meForEdit(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        var user = currentUserService.require(principal);
        return ResponseEntity.ok(
                paymentProfileService.getActiveProfile(user.getId())
                        .map(paymentProfileService::toEditResponse)
                        .orElseGet(() -> paymentProfileService.toEditResponse(null))
        );
    }

    @PostMapping
    public ResponseEntity<PaymentProfileMeResponse> create(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody PaymentProfileUpsertRequest request
    ) {
        var user = currentUserService.require(principal);
        PaymentProfileEntity saved = paymentProfileService.create(user.getId(), request);
        return ResponseEntity.ok(paymentProfileService.toResponse(saved));
    }

    @PutMapping
    public ResponseEntity<PaymentProfileMeResponse> update(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody PaymentProfileUpsertRequest request
    ) {
        var user = currentUserService.require(principal);
        PaymentProfileEntity saved = paymentProfileService.upsert(user.getId(), request);
        return ResponseEntity.ok(paymentProfileService.toResponse(saved));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        var user = currentUserService.require(principal);
        paymentProfileService.delete(user.getId());
        return ResponseEntity.noContent().build();
    }
}
