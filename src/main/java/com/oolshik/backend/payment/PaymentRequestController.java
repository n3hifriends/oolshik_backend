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
            String upi = PaymentRequestService.buildUpiIntent(saved);
            PaymentResponse out = new PaymentResponse();
            out.id = saved.getId();
            out.upiIntent = upi;
            out.snapshot = new PaymentResponse.Snapshot();
            out.snapshot.taskId = saved.getTaskId();
            out.snapshot.payeeVpa = saved.getPayeeVpa();
            out.snapshot.payeeName = saved.getPayeeName();
            out.snapshot.mcc = saved.getMcc();
            out.snapshot.merchantId = saved.getMerchantId();
            out.snapshot.amountRequested = saved.getAmountRequested();
            out.snapshot.currency = saved.getCurrency();
            out.snapshot.note = saved.getNote();
            out.snapshot.createdAt = saved.getCreatedAt();
            out.snapshot.expiresAt = saved.getExpiresAt();
            out.snapshot.status = saved.getStatus();
            return ResponseEntity.created(URI.create("/api/payments/" + saved.getId()))
                    .body(out);
        } catch (NoSuchAlgorithmException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        PaymentRequest pr = requireOwnedPayment(id, caller.getId());
        String upi = PaymentRequestService.buildUpiIntent(pr);
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("payeeVpa", pr.getPayeeVpa());
        snapshot.put("payeeName", pr.getPayeeName());
        snapshot.put("mcc", pr.getMcc());
        snapshot.put("merchantId", pr.getMerchantId());
        snapshot.put("amountRequested", pr.getAmountRequested());
        snapshot.put("currency", pr.getCurrency());
        snapshot.put("note", pr.getNote());
        snapshot.put("createdAt", pr.getCreatedAt());
        snapshot.put("expiresAt", pr.getExpiresAt());

        Map<String, Object> bodyOut = new java.util.LinkedHashMap<>();
        bodyOut.put("id", pr.getId());
        bodyOut.put("taskId", pr.getTaskId());
        bodyOut.put("status", pr.getStatus());
        bodyOut.put("snapshot", snapshot);
        bodyOut.put("upiIntent", upi);
        return ResponseEntity.ok(bodyOut);
    }

    @PostMapping("/{id}/initiate")
    public ResponseEntity<Map<String, Object>> initiate(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId,
            @RequestBody(required = false) InitiatePaymentRequest body) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        requireOwnedPayment(id, caller.getId());
        PaymentRequest pr = service.markInitiated(id);
        return ResponseEntity.ok(Map.of("status", pr.getStatus()));
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<Map<String, Object>> markPaid(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId,
            @RequestBody(required = false) MarkPaidRequest body) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        requireOwnedPayment(id, caller.getId());
        PaymentRequest pr =
                service.markPaid(id, body == null ? null : body.paidAmount(), body == null ? null : body.proofUrl());
        return ResponseEntity.ok(Map.of("status", pr.getStatus()));
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<Map<String, Object>> dispute(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @PathVariable("id") String rawId,
            @RequestBody Map<String, String> body) {
        UserEntity caller = requireAuthenticatedUser(principal);
        UUID id = requireUuid(rawId, "id");
        requireOwnedPayment(id, caller.getId());
        PaymentRequest pr = service.dispute(id, body.get("reason"));
        return ResponseEntity.ok(Map.of("status", pr.getStatus()));
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

    private PaymentRequest requireOwnedPayment(UUID id, UUID userId) {
        PaymentRequest pr;
        try {
            pr = service.get(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment request not found", ex);
        }
        if (pr.getScannedByUser() == null || !pr.getScannedByUser().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your payment request");
        }
        return pr;
    }
}
