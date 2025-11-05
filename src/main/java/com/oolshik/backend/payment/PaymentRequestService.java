package com.oolshik.backend.payment;

import com.oolshik.backend.payment.dto.PaymentDtos.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentRequestService {

    private final PaymentRequestRepository repo;

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    public PaymentRequest create(UUID scannerUserId, String clientIp, CreatePaymentRequest in) throws NoSuchAlgorithmException {
        var id = UUID.randomUUID();

        var pr = PaymentRequest.builder()
                .id(id)
                .taskId(in.taskId())
                .scannedByUser(scannerUserId)
                .rawPayload(in.rawPayload())
                .rawSha256(sha256(in.rawPayload()))
                .format(in.format())
                .payeeVpa(in.payeeVpa())
                .payeeName(in.payeeName())
                .mcc(in.mcc())
                .merchantId(in.merchantId())
                .amountRequested(in.amount())
                .currency(Optional.ofNullable(in.currency()).orElse("INR"))
                .note(in.note())
                .status("PENDING")
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

        return repo.save(pr);
    }

    public PaymentRequest get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("payment_request not found"));
    }

    public PaymentRequest markInitiated(UUID id) {
        var pr = get(id);
        if ("PENDING".equals(pr.getStatus())) {
            pr.setStatus("INITIATED");
            return repo.save(pr);
        }
        return pr;
    }

    public PaymentRequest markPaid(UUID id, BigDecimal paidAmount, String proofUrl) {
        var pr = get(id);
        pr.setStatus("PAID_MARKED");
        // Optionally: store paidAmount & proofUrl in a separate evidence table later
        if (paidAmount != null && pr.getAmountRequested() == null) {
            pr.setAmountRequested(paidAmount);
        }
        return repo.save(pr);
    }

    public PaymentRequest dispute(UUID id, String reason) {
        var pr = get(id);
        pr.setStatus("DISPUTED");
        return repo.save(pr);
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
