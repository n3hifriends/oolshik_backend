package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestRatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HelpRequestRatingRepository extends JpaRepository<HelpRequestRatingEntity, UUID> {
    boolean existsByRequestIdAndRaterUserId(UUID requestId, UUID raterUserId);

    @org.springframework.data.jpa.repository.Query(
            value = """
                SELECT AVG(r.rating_value)::numeric(3,2)
                FROM help_request_rating r
                WHERE r.target_user_id = :targetUserId
                """,
            nativeQuery = true
    )
    java.math.BigDecimal findAvgRatingForUser(@org.springframework.data.repository.query.Param("targetUserId") UUID targetUserId);

    @org.springframework.data.jpa.repository.Query(
            value = """
                SELECT r.rating_value
                FROM help_request_rating r
                WHERE r.request_id = :requestId
                  AND r.rater_user_id = :raterUserId
                """,
            nativeQuery = true
    )
    java.math.BigDecimal findRatingForRequestAndRater(
            @org.springframework.data.repository.query.Param("requestId") UUID requestId,
            @org.springframework.data.repository.query.Param("raterUserId") UUID raterUserId
    );
}
