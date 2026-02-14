package com.oolshik.backend.payment.dto;

import com.oolshik.backend.payment.PaymentPayerRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentResponse {
    public static final class Snapshot {
        public UUID taskId;
        public String payeeVpa;
        public String payeeName;
        public String mcc;
        public String merchantId;
        public String txnRef;
        public BigDecimal amountRequested;
        public String currency;
        public String note;
        public Instant createdAt;
        public Instant expiresAt;
        public String status;
    }

    public UUID id;
    public UUID taskId;
    public String status;
    public Snapshot snapshot;
    public String upiIntent;
    public UUID payerUserId;
    public PaymentPayerRole payerRole;
    public UUID requesterUserId;
    public UUID helperUserId;
    public Boolean canPay;
}
