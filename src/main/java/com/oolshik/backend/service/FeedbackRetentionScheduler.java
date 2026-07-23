package com.oolshik.backend.service;

import com.oolshik.backend.domain.FeedbackStatus;
import com.oolshik.backend.repo.FeedbackEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class FeedbackRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedbackRetentionScheduler.class);

    private final FeedbackEventRepository feedbackRepo;

    public FeedbackRetentionScheduler(FeedbackEventRepository feedbackRepo) {
        this.feedbackRepo = feedbackRepo;
    }

    @Scheduled(fixedDelayString = "${app.feedback.retentionPurgeIntervalMs:86400000}")
    @Transactional
    public void purgeExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now();
        int deleted = feedbackRepo.deleteExpired(cutoff, List.of(FeedbackStatus.OPEN, FeedbackStatus.REVIEWING));
        if (deleted > 0) {
            log.info("feedback.retention.purge deleted={}", deleted);
        }
    }
}
