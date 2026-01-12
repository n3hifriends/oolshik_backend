package com.oolshik.backend.transcription;

import com.oolshik.backend.config.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TranscriptionResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionResultConsumer.class);

    private final TranscriptionResultService resultService;
    private final KafkaTopicProperties topics;

    public TranscriptionResultConsumer(TranscriptionResultService resultService,
                                       KafkaTopicProperties topics) {
        this.resultService = resultService;
        this.topics = topics;
    }

    @KafkaListener(topics = "${app.kafka.topics.sttResults}",
            containerFactory = "sttResultKafkaListenerContainerFactory")
    public void onMessage(SttResultMessage message) {
        if (message == null) return;
        if (message.correlationId() != null) {
            MDC.put("cid", message.correlationId());
        }
        try {
            resultService.applyResult(message);
        } catch (Exception ex) {
            log.error("stt.results consume failed jobId={} taskId={} err={}",
                    message.jobId(), message.taskId(), ex.toString(), ex);
            throw ex;
        } finally {
            if (message.correlationId() != null) {
                MDC.remove("cid");
            }
        }
    }
}
