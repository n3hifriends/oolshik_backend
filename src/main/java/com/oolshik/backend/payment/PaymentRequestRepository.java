package com.oolshik.backend.payment;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {
    Optional<PaymentRequest> findByIdAndTaskId(UUID id, UUID taskId);

    Optional<PaymentRequest> findFirstByTaskIdAndStatusInOrderByCreatedAtDesc(UUID taskId, Collection<String> statuses);

    Optional<PaymentRequest> findFirstByTaskIdAndPaymentModeAndStatusInOrderByCreatedAtDesc(
            UUID taskId,
            PaymentMode paymentMode,
            Collection<String> statuses
    );

    List<PaymentRequest> findByTaskIdAndStatusInOrderByCreatedAtDesc(UUID taskId, Collection<String> statuses);

    long countByStatus(String status);

    @Query("select coalesce(sum(p.amountRequested), 0) from PaymentRequest p where p.status = 'PAID_MARKED'")
    BigDecimal sumCapturedAmount();

    @Query(value = """
            select p from PaymentRequest p
            where (:status is null or p.status = :status)
              and (:mode is null or p.paymentMode = :mode)
            order by p.createdAt desc
            """,
            countQuery = """
            select count(p) from PaymentRequest p
            where (:status is null or p.status = :status)
              and (:mode is null or p.paymentMode = :mode)
            """)
    Page<PaymentRequest> findForAdmin(@Param("status") String status,
                                      @Param("mode") PaymentMode mode,
                                      Pageable pageable);

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
