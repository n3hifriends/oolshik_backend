package com.oolshik.backend.payment;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {
    Optional<PaymentRequest> findByIdAndTaskId(UUID id, UUID taskId);

    Optional<PaymentRequest> findFirstByTaskIdAndStatusInOrderByCreatedAtDesc(UUID taskId, Collection<String> statuses);

    @Query(value = """
        SELECT *
          FROM payment_requests
         WHERE status IN (:statuses)
           AND expires_at IS NOT NULL
           AND expires_at <= :now
         ORDER BY expires_at ASC
         FOR UPDATE SKIP LOCKED
         LIMIT :limit
        """, nativeQuery = true)
    List<PaymentRequest> lockExpiredActive(
            @Param("statuses") List<String> statuses,
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}
