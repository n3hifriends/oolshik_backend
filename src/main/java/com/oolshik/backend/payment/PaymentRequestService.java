package com.oolshik.backend.payment;

import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.payment.dto.PaymentDtos.*;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.service.PaymentNotificationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentRequestService {

    private final PaymentRequestRepository repo;
    private final HelpRequestRepository helpRequestRepository;
    private final PaymentNotificationService paymentNotificationService;

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_INITIATED = "INITIATED";
    public static final String STATUS_PAID_MARKED = "PAID_MARKED";
    public static final String STATUS_DISPUTED = "DISPUTED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    private static final List<String> ACTIVE_STATUSES = List.of(STATUS_PENDING, STATUS_INITIATED);

    @Transactional
    public PaymentRequest create(UUID scannerUserId, String clientIp, CreatePaymentRequest in) throws NoSuchAlgorithmException {
        PaymentRequest existing = repo.findFirstByTaskIdAndStatusInOrderByCreatedAtDesc(in.taskId(), ACTIVE_STATUSES)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        HelpRequestEntity task = helpRequestRepository.findById(in.taskId())
                .orElseThrow(() -> new IllegalArgumentException("task not found"));
        var id = UUID.randomUUID();
        PaymentPayerRole payerRole = in.payerRole() == null ? PaymentPayerRole.HELPER : in.payerRole();
        UUID payerUser = resolvePayerUser(task, scannerUserId, payerRole);

        var pr = PaymentRequest.builder()
                .id(id)
                .taskId(in.taskId())
                .scannedByUser(scannerUserId)
                .requesterUser(task.getRequesterId())
                .helperUser(task.getHelperId())
                .payerUser(payerUser)
                .payerRole(payerRole)
                .rawPayload(in.rawPayload())
                .rawSha256(sha256(in.rawPayload()))
                .format(in.format())
                .payeeVpa(in.payeeVpa())
                .payeeName(in.payeeName())
                .mcc(in.mcc())
                .merchantId(in.merchantId())
                .txnRef(in.txnRef())
                .amountRequested(in.amount())
                .currency(Optional.ofNullable(in.currency()).orElse("INR"))
                .note(in.note())
                .status(STATUS_PENDING)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .appVersion(in.appVersion())
                .deviceId(in.deviceId())
                .createdByIp(toInet(clientIp))
                .build();

        if (in.scanLocation() != null && in.scanLocation().lat() != null && in.scanLocation().lon() != null) {
            double lat = in.scanLocation().lat();
            double lon = in.scanLocation().lon();
            pr.setScanLocation(GEO.createPoint(new Coordinate(lon, lat)));
        }

        PaymentRequest saved = repo.save(pr);
        paymentNotificationService.enqueuePaymentEvent(
                NotificationEventType.PAYMENT_REQUEST_CREATED,
                saved,
                scannerUserId,
                null,
                saved.getStatus()
        );
        if (!scannerUserId.equals(saved.getPayerUser())) {
            paymentNotificationService.enqueuePaymentEvent(
                    NotificationEventType.PAYMENT_ACTION_REQUIRED,
                    saved,
                    scannerUserId,
                    saved.getStatus(),
                    saved.getStatus()
            );
        }
        return saved;
    }

    public PaymentRequest get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("payment_request not found"));
    }

    public PaymentRequest getActiveForTask(UUID taskId) {
        return repo.findFirstByTaskIdAndStatusInOrderByCreatedAtDesc(taskId, ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("active payment request not found"));
    }

    @Transactional
    public PaymentRequest markInitiated(UUID id, UUID actorUserId) {
        var pr = get(id);
        if (!canPay(pr, actorUserId)) {
            throw new SecurityException("Only payer can initiate payment");
        }
        if (STATUS_PENDING.equals(pr.getStatus())) {
            String previous = pr.getStatus();
            pr.setStatus(STATUS_INITIATED);
            PaymentRequest saved = repo.save(pr);
            paymentNotificationService.enqueuePaymentEvent(
                    NotificationEventType.PAYMENT_INITIATED,
                    saved,
                    actorUserId,
                    previous,
                    saved.getStatus()
            );
            return saved;
        }
        return pr;
    }

    @Transactional
    public PaymentRequest markPaid(UUID id, UUID actorUserId, BigDecimal paidAmount, String proofUrl) {
        var pr = get(id);
        if (!canPay(pr, actorUserId)) {
            throw new SecurityException("Only payer can mark payment as paid");
        }
        if (STATUS_PAID_MARKED.equals(pr.getStatus())) {
            return pr;
        }
        String previous = pr.getStatus();
        pr.setStatus(STATUS_PAID_MARKED);
        // Optionally: store paidAmount & proofUrl in a separate evidence table later
        if (paidAmount != null && pr.getAmountRequested() == null) {
            pr.setAmountRequested(paidAmount);
        }
        PaymentRequest saved = repo.save(pr);
        paymentNotificationService.enqueuePaymentEvent(
                NotificationEventType.PAYMENT_MARKED_PAID,
                saved,
                actorUserId,
                previous,
                saved.getStatus()
        );
        return saved;
    }

    @Transactional
    public PaymentRequest dispute(UUID id, UUID actorUserId, String reason) {
        var pr = get(id);
        if (!isTaskParticipant(pr, actorUserId)) {
            throw new SecurityException("Only task participants can dispute payment");
        }
        if (STATUS_DISPUTED.equals(pr.getStatus())) {
            return pr;
        }
        String previous = pr.getStatus();
        pr.setStatus(STATUS_DISPUTED);
        PaymentRequest saved = repo.save(pr);
        paymentNotificationService.enqueuePaymentEvent(
                NotificationEventType.PAYMENT_DISPUTED,
                saved,
                actorUserId,
                previous,
                saved.getStatus()
        );
        return saved;
    }

    @Transactional
    public int expireActiveRequests(int limit) {
        List<PaymentRequest> expired = repo.lockExpiredActive(ACTIVE_STATUSES, Instant.now(), limit);
        for (PaymentRequest payment : expired) {
            String previous = payment.getStatus();
            payment.setStatus(STATUS_EXPIRED);
            PaymentRequest saved = repo.save(payment);
            paymentNotificationService.enqueuePaymentEvent(
                    NotificationEventType.PAYMENT_EXPIRED,
                    saved,
                    null,
                    previous,
                    saved.getStatus()
            );
        }
        return expired.size();
    }

    public boolean isTaskParticipant(PaymentRequest pr, UUID userId) {
        if (userId == null || pr == null) return false;
        return userId.equals(pr.getRequesterUser())
                || userId.equals(pr.getHelperUser())
                || userId.equals(pr.getPayerUser())
                || userId.equals(pr.getScannedByUser());
    }

    public boolean canPay(PaymentRequest pr, UUID userId) {
        return userId != null && pr != null && userId.equals(pr.getPayerUser());
    }

    private UUID resolvePayerUser(HelpRequestEntity task, UUID scannerUserId, PaymentPayerRole payerRole) {
        if (payerRole == PaymentPayerRole.REQUESTER) {
            return task.getRequesterId();
        }
        if (task.getHelperId() != null) {
            return task.getHelperId();
        }
        return scannerUserId;
    }

    public static String buildUpiIntent(PaymentRequest pr) {
        // Canonical UPI intent from snapshot to avoid client tampering
        // upi://pay?pa=..&pn=..&am=..&cu=INR&tn=Oolshik%20Task%20<taskId>
        var base = new StringBuilder("upi://pay?");
        if (notBlank(pr.getPayeeVpa())) base.append("pa=").append(url(pr.getPayeeVpa())).append("&");
        if (notBlank(pr.getPayeeName())) base.append("pn=").append(url(pr.getPayeeName())).append("&");
        if (pr.getAmountRequested() != null) base.append("am=").append(pr.getAmountRequested()).append("&");
        base.append("cu=").append(url(Optional.ofNullable(pr.getCurrency()).orElse("INR"))).append("&");
        var tn = Optional.ofNullable(pr.getNote()).orElse("Oolshik Task " + pr.getTaskId());
        base.append("tn=").append(url(tn));
        return base.toString();
    }

    private static InetAddress toInet(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sha256(String raw) throws NoSuchAlgorithmException {
        // Spring core has DigestUtils for MD5; for SHA-256 use JDK
        var md = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256);
        var bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        var sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
