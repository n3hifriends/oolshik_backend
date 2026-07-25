package com.oolshik.backend.transcription;

import com.oolshik.backend.config.KafkaTopicProperties;
import com.oolshik.backend.media.AudioFile;
import com.oolshik.backend.media.AudioFileRepository;
import com.oolshik.backend.media.AudioStorageMetadataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@ConditionalOnProperty(name = "app.messaging.kafka.stt.enabled", havingValue = "true")
public class KafkaTranscriptionJobPublisher implements TranscriptionJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTranscriptionJobPublisher.class);

    private final KafkaTemplate<String, SttJobMessage> kafkaTemplate;
    private final KafkaTopicProperties topics;
    private final AudioFileRepository audioRepo;
    private final AudioStorageMetadataResolver metadataResolver;

    public KafkaTranscriptionJobPublisher(
                                          @Qualifier("sttKafkaTemplate") KafkaTemplate<String, SttJobMessage> kafkaTemplate,
                                          KafkaTopicProperties topics,
                                          AudioFileRepository audioRepo,
                                          AudioStorageMetadataResolver metadataResolver) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.audioRepo = audioRepo;
        this.metadataResolver = metadataResolver;
    }

    @Override
    public void publishJob(TranscriptionJobEntity job) {
        String cid = MDC.get("cid");
        AudioFile audioFile = job.getAudioFileId() == null ? null : audioRepo.findById(job.getAudioFileId()).orElse(null);
        AudioStorageMetadataResolver.StorageReference storageRef = audioFile == null ? null : metadataResolver.resolve(audioFile);
        SttJobMessage message = new SttJobMessage(
                job.getJobId(),
                job.getTaskId(),
                job.getAudioFileId(),
                storageRef != null ? storageRef.provider() : null,
                storageRef != null ? storageRef.bucket() : null,
                storageRef != null ? storageRef.objectKey() : null,
                storageRef != null ? storageRef.region() : null,
                storageRef != null ? storageRef.endpoint() : null,
                storageRef != null ? storageRef.pathStyleAccessEnabled() : null,
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
