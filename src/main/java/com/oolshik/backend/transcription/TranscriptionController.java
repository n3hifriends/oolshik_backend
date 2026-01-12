package com.oolshik.backend.transcription;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/transcriptions")
public class TranscriptionController {

    private final TranscriptionJobRepository repository;

    public TranscriptionController(TranscriptionJobRepository repository) {
        this.repository = repository;
    }

    public record TranscriptionView(
            UUID jobId,
            UUID taskId,
            TranscriptionStatus status,
            String transcriptText,
            String engine,
            String modelVersion,
            OffsetDateTime updatedAt
    ) {}

    @GetMapping("/{jobId}")
    public TranscriptionView get(@PathVariable UUID jobId) {
        TranscriptionJobEntity job = repository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcription job not found"));

        return new TranscriptionView(
                job.getJobId(),
                job.getTaskId(),
                job.getStatus(),
                job.getTranscriptText(),
                job.getEngine(),
                job.getModelVersion(),
                job.getUpdatedAt()
        );
    }
}
