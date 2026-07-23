package com.oolshik.backend.repo;

import com.oolshik.backend.entity.FeedbackActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackActionRepository extends JpaRepository<FeedbackActionEntity, UUID> {
    List<FeedbackActionEntity> findTop50ByFeedbackIdOrderByCreatedAtDesc(UUID feedbackId);
}
