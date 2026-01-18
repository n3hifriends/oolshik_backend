package com.oolshik.backend.transcription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.repo.HelpRequestRepository;

@Service
public class TranscriptionResultService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionResultService.class);

    private final TranscriptionJobRepository repository;
    private final HelpRequestRepository helpRequestRepository;

    public TranscriptionResultService(TranscriptionJobRepository repository,
                                      HelpRequestRepository helpRequestRepository) {
        this.repository = repository;
        this.helpRequestRepository = helpRequestRepository;
    }

    @Transactional
    public void applyResult(SttResultMessage result) {
        TranscriptionJobEntity job = repository.findById(result.jobId()).orElse(null);
        if (job == null) {
            log.warn("Transcription job not found for result jobId={} taskId={}",
                    result.jobId(), result.taskId());
            return;
        }

        if (job.getStatus() == TranscriptionStatus.COMPLETED) {
            log.info("Ignoring duplicate result for completed job jobId={} taskId={}",
                    job.getJobId(), job.getTaskId());
            return;
        }

        TranscriptionStatus status = result.status();
        if (status == null) {
            throw new IllegalArgumentException("Result status is required");
        }

        job.setStatus(status);
        if (result.transcriptText() != null) {
            job.setTranscriptText(result.transcriptText());
        }
        if (result.detectedLanguage() != null) {
            job.setDetectedLanguage(result.detectedLanguage());
        }
        if (result.confidence() != null) {
            job.setConfidence(result.confidence());
        }
        if (result.engine() != null) {
            job.setEngine(result.engine());
        }
        if (result.modelVersion() != null) {
            job.setModelVersion(result.modelVersion());
        }
        job.setLastErrorCode(result.errorCode());
        job.setLastErrorMessage(result.errorMessage());

        repository.save(job);

        if (status == TranscriptionStatus.COMPLETED) {
            applyTranscriptToTask(job.getTaskId(), result.transcriptText());
        }
        log.info("Updated transcription job jobId={} taskId={} status={}",
                job.getJobId(), job.getTaskId(), job.getStatus());
    }

    private void applyTranscriptToTask(java.util.UUID taskId, String transcriptText) {
        if (transcriptText == null || transcriptText.isBlank()) {
            return;
        }
        HelpRequestEntity task = helpRequestRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Help request not found for transcription taskId={}", taskId);
            return;
        }

        boolean titleMissing = task.getTitle() == null || task.getTitle().isBlank() || task.getTitle().equals("...");
        boolean descriptionMissing = task.getDescription() == null || task.getDescription().isBlank();
        if (!(task.getStatus() == HelpRequestStatus.DRAFT || titleMissing || descriptionMissing)) {
            return;
        }

        String transcript = transcriptText.strip();
        if (titleMissing) {
            task.setTitle(buildTitle(transcript));
        }
        if (descriptionMissing) {
            task.setDescription(transcript);
        }
        if (task.getStatus() == HelpRequestStatus.DRAFT) {
            task.setStatus(HelpRequestStatus.OPEN);
        }
        helpRequestRepository.save(task);
        log.info("Updated help request from transcript taskId={} status={}", taskId, task.getStatus());
    }

    private String buildTitle(String transcript) {
        String candidate = transcript;
        int newline = candidate.indexOf('\n');
        if (newline > 0) {
            candidate = candidate.substring(0, newline);
        }
        int sentenceEnd = firstSentenceEnd(candidate);
        if (sentenceEnd >= 0) {
            candidate = candidate.substring(0, sentenceEnd + 1);
        }
        candidate = candidate.strip();
        if (candidate.length() > 120) {
            candidate = candidate.substring(0, 120).strip();
        }
        return candidate.isEmpty() ? "Voice task" : candidate;
    }

    private int firstSentenceEnd(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return i;
            }
        }
        return -1;
    }
}
