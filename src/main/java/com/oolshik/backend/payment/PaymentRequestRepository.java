package com.oolshik.backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {
    Optional<PaymentRequest> findByIdAndTaskId(UUID id, UUID taskId);
}