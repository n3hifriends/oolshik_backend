package com.oolshik.backend.transcription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oolshik.backend.domain.HelpRequestStatus;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.notification.NotificationEventContext;
import com.oolshik.backend.notification.NotificationEventType;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.service.HelpRequestNotificationService;

@Service
public class TranscriptionResultService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionResultService.class);

    private final TranscriptionJobRepository repository;
    private final HelpRequestRepository helpRequestRepository;
    private final HelpRequestNotificationService notificationService;

    public TranscriptionResultService(TranscriptionJobRepository repository,
                                      HelpRequestRepository helpRequestRepository,
                                      HelpRequestNotificationService notificationService) {
        this.repository = repository;
        this.helpRequestRepository = helpRequestRepository;
        this.notificationService = notificationService;
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
            throw new IllegalArgumentException("errors.transcription.resultStatusRequired");
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

        String transcript = collapseRepeatedTranscript(transcriptText.strip());
        if (titleMissing) {
            task.setTitle(buildTitle(transcript));
        }
        if (descriptionMissing) {
            task.setDescription(transcript);
        }
        String previousStatus = task.getStatus().name();
        if (task.getStatus() == HelpRequestStatus.DRAFT) {
            task.setStatus(HelpRequestStatus.OPEN);
        }
        helpRequestRepository.save(task);
        log.info("Updated help request from transcript taskId={} status={}", taskId, task.getStatus());

        NotificationEventContext ctx = new NotificationEventContext();
        ctx.setPreviousStatus(previousStatus);
        ctx.setNewStatus(task.getStatus().name());
        notificationService.enqueueTaskEvent(NotificationEventType.TASK_CREATED, task, ctx);
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

    // Detects Whisper hallucination loops where the same sentence is repeated many times
    // and collapses the transcript down to the unique first occurrence.
    private String collapseRepeatedTranscript(String text) {
        if (text == null || text.isBlank() || text.length() < 20) return text;

        // Split on sentence-ending punctuation (includes Devanagari danda ।)
        String[] parts = text.split("(?<=[.!?।])\\s+");
        if (parts.length < 3) return text;

        String first = parts[0].strip();
        if (first.isEmpty()) return text;

        // If 70%+ of all sentences are identical to the first, it's a hallucination loop
        long matches = Arrays.stream(parts)
                .filter(p -> p.strip().equalsIgnoreCase(first))
                .count();
        if (matches * 10 >= parts.length * 7) {
            log.warn("Collapsed repeated transcript: {} occurrences of '{}' in {} parts",
                    matches, first.length() > 60 ? first.substring(0, 60) + "…" : first, parts.length);
            return first;
        }

        // More general case: stop at the first repeated sentence
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!seen.add(part.strip().toLowerCase())) break;
            if (!result.isEmpty()) result.append(" ");
            result.append(part.strip());
        }
        String collapsed = result.toString().strip();
        // Only use collapsed version if it's meaningfully shorter (at least 40% shorter)
        if (collapsed.length() < text.length() * 0.6) {
            log.warn("Collapsed repeated transcript from {} to {} chars", text.length(), collapsed.length());
            return collapsed;
        }
        return text;
    }
}
