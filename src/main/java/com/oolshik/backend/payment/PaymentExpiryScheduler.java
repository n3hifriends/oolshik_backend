package com.oolshik.backend.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);
    private final PaymentRequestService paymentRequestService;

    public PaymentExpiryScheduler(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    @Scheduled(fixedDelayString = "${app.payment.expirySweepIntervalMs:60000}")
    public void expirePayments() {
        int expired = paymentRequestService.expireActiveRequests(50);
        if (expired > 0) {
            log.info("payment expiry sweep expired={}", expired);
        }
    }
}
