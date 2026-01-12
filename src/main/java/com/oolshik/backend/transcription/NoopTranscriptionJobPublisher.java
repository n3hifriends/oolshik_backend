package com.oolshik.backend.transcription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(TranscriptionJobPublisher.class)
public class NoopTranscriptionJobPublisher implements TranscriptionJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopTranscriptionJobPublisher.class);

    @Override
    public void publishJob(TranscriptionJobEntity job) {
        String cid = MDC.get("cid");
        log.info("[{}] stt.jobs publish skipped (noop) jobId={} taskId={}", cid, job.getJobId(), job.getTaskId());
    }
}
