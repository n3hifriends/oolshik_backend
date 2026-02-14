package com.oolshik.backend.payment;

import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oolshik.backend.payment.dto.PaymentDtos.InitiatePaymentRequest;
import com.oolshik.backend.payment.dto.PaymentDtos.MarkPaidRequest;
import com.oolshik.backend.payment.dto.PaymentResponse;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentRequestController {
    private final PaymentRequestService service;
    private final UserRepository userRepository;

    public PaymentRequestController(PaymentRequestService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @PostMapping("/qr-scan")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            HttpServletRequest http,
            @Valid @RequestBody CreatePaymentRequest body) {
        UserEntity scanner = requireAuthenticatedUser(principal);
        UUID userId = scanner.getId();
        String ip = clientIp(http);

        try {
            PaymentRequest saved = service.create(userId, ip, body);
            PaymentResponse out = toResponse(saved, userId);
            return ResponseEntity.created(URI.create("/api/payments/" + saved.getId()))
                    .body(out);
        } catch (NoSuchAlgorithmException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> get(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        PaymentRequest pr = requireParticipantPayment(id, caller.getId());
        return ResponseEntity.ok(toResponse(pr, caller.getId()));
    }

    @GetMapping("/task/{taskId}/active")
    public ResponseEntity<PaymentResponse> getActiveByTask(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("taskId") String rawTaskId
    ) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID taskId = requireUuid(rawTaskId, "taskId");
        PaymentRequest active;
        try {
            active = service.getActiveForTask(taskId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active payment request not found", ex);
        }
        if (!service.isTaskParticipant(active, caller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your payment request");
        }
        return ResponseEntity.ok(toResponse(active, caller.getId()));
    }

    @PostMapping("/{id}/initiate")
    public ResponseEntity<Map<String, Object>> initiate(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId,
            @RequestBody(required = false) InitiatePaymentRequest body) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        PaymentRequest pr = requireParticipantPayment(id, caller.getId());
        requirePayer(pr, caller.getId());
        PaymentRequest updated = service.markInitiated(id, caller.getId());
        return ResponseEntity.ok(Map.of("status", updated.getStatus()));
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<Map<String, Object>> markPaid(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId,
            @RequestBody(required = false) MarkPaidRequest body) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        PaymentRequest pr = requireParticipantPayment(id, caller.getId());
        requirePayer(pr, caller.getId());
        PaymentRequest updated =
                service.markPaid(id, caller.getId(), body == null ? null : body.paidAmount(), body == null ? null : body.proofUrl());
        return ResponseEntity.ok(Map.of("status", updated.getStatus()));
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<Map<String, Object>> dispute(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId,
            @RequestBody Map<String, String> body) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        requireParticipantPayment(id, caller.getId());
        PaymentRequest updated = service.dispute(id, caller.getId(), body == null ? null : body.get("reason"));
        return ResponseEntity.ok(Map.of("status", updated.getStatus()));
    }

    private PaymentResponse toResponse(PaymentRequest pr, UUID callerUserId) {
        String upi = PaymentRequestService.buildUpiIntent(pr);
        PaymentResponse out = new PaymentResponse();
        out.id = pr.getId();
        out.taskId = pr.getTaskId();
        out.status = pr.getStatus();
        out.upiIntent = upi;
        out.payerUserId = pr.getPayerUser();
        out.payerRole = pr.getPayerRole();
        out.requesterUserId = pr.getRequesterUser();
        out.helperUserId = pr.getHelperUser();
        out.canPay = service.canPay(pr, callerUserId);

        out.snapshot = new PaymentResponse.Snapshot();
        out.snapshot.taskId = pr.getTaskId();
        out.snapshot.payeeVpa = pr.getPayeeVpa();
        out.snapshot.payeeName = pr.getPayeeName();
        out.snapshot.mcc = pr.getMcc();
        out.snapshot.merchantId = pr.getMerchantId();
        out.snapshot.amountRequested = pr.getAmountRequested();
        out.snapshot.currency = pr.getCurrency();
        out.snapshot.note = pr.getNote();
        out.snapshot.createdAt = pr.getCreatedAt();
        out.snapshot.expiresAt = pr.getExpiresAt();
        out.snapshot.status = pr.getStatus();
        return out;
    }

    private void requirePayer(PaymentRequest pr, UUID userId) {
        if (!service.canPay(pr, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only payer can perform this action");
        }
    }

    private PaymentRequest requireParticipantPayment(UUID id, UUID userId) {
        PaymentRequest pr;
        try {
            pr = service.get(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment request not found", ex);
        }
        if (!service.isTaskParticipant(pr, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your payment request");
        }
        return pr;
    }

    private static UUID requireUuid(String rawValue, String parameterName) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalArgumentException("Value is blank");
            }
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format(
                            "Invalid %s. Expected a UUID in the format xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.",
                            parameterName),
                    ex);
        }
    }

    private static String clientIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        return h != null ? h.split(",")[0].trim() : req.getRemoteAddr();
    }

    private UserEntity requireAuthenticatedUser(FirebaseTokenFilter.FirebaseUserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        UserEntity user = resolveUser(principal);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not registered");
        }
        return user;
    }

    private UserEntity resolveUser(FirebaseTokenFilter.FirebaseUserPrincipal principal) {
        if (principal == null) return null;
        if (principal.uid() != null) {
            var byUid = userRepository.findByFirebaseUid(principal.uid());
            if (byUid.isPresent()) return byUid.get();
        }
        String phone = principal.phone();
        if (phone != null) {
            return userRepository.findByPhoneNumber(phone).orElse(null);
        }
        return null;
    }
}
