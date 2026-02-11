package com.oolshik.backend.transcription;

import com.oolshik.backend.config.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class KafkaTranscriptionJobPublisher implements TranscriptionJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTranscriptionJobPublisher.class);

    private final KafkaTemplate<String, SttJobMessage> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public KafkaTranscriptionJobPublisher(
                                          @Qualifier("sttKafkaTemplate") KafkaTemplate<String, SttJobMessage> kafkaTemplate,
                                          KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void publishJob(TranscriptionJobEntity job) {
        String cid = MDC.get("cid");
        SttJobMessage message = new SttJobMessage(
                job.getJobId(),
                job.getTaskId(),
                job.getAudioUrl(),
                job.getLanguageHint(),
                OffsetDateTime.now(),
                cid
        );
        kafkaTemplate.send(topics.getSttJobs(), job.getJobId().toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[{}] stt.jobs publish failed jobId={} taskId={} err={}",
                                cid, job.getJobId(), job.getTaskId(), ex.toString());
                    } else {
                        log.info("[{}] stt.jobs published jobId={} taskId={} offset={}",
                                cid, job.getJobId(), job.getTaskId(),
                                result != null ? result.getRecordMetadata().offset() : null);
                    }
                });
    }
}
