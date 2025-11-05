package com.oolshik.backend.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_requests")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRequest {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private UUID scannedByUser;

    @Column(nullable = false, columnDefinition = "text")
    private String rawPayload;

    @Column(nullable = false, length = 64)
    private String rawSha256;

    @Column(nullable = false, length = 16)
    private String format; // upi-uri | emv | unknown

    private String payeeVpa;
    private String payeeName;
    private String mcc;
    private String merchantId;

    private BigDecimal amountRequested;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Column(length = 256)
    private String note;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point scanLocation;

    @Column(nullable = false, length = 24)
    private String status; // PENDING | INITIATED | PAID_MARKED | DISPUTED | CANCELLED

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    private String appVersion;
    private String deviceId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(columnDefinition = "inet")
    private InetAddress createdByIp;
}
