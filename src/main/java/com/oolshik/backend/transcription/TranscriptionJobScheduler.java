package com.oolshik.backend.transcription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TranscriptionJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionJobScheduler.class);

    private final TranscriptionJobRepository repository;
    private final TranscriptionJobPublisher publisher;

    public TranscriptionJobScheduler(TranscriptionJobRepository repository,
                                     TranscriptionJobPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.transcription.republishDelayMs:30000}")
    public void republishPendingJobs() {
        List<TranscriptionJobEntity> pending = repository
                .findTop50ByStatusOrderByUpdatedAtAsc(TranscriptionStatus.PENDING);
        if (pending.isEmpty()) return;

        log.info("Republishing {} pending transcription jobs", pending.size());
        for (TranscriptionJobEntity job : pending) {
            publisher.publishJob(job);
        }
    }
}
