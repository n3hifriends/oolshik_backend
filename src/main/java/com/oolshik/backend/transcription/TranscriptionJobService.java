package com.oolshik.backend.transcription;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TranscriptionJobService {

    private final TranscriptionJobRepository repository;

    public TranscriptionJobService(TranscriptionJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TranscriptionJobEntity createOrGet(UUID taskId,
                                              String audioUrl,
                                              String languageHint,
                                              String engine,
                                              String modelVersion) {
        Optional<TranscriptionJobEntity> existing = repository.findByTaskId(taskId);
        if (existing.isPresent()) {
            return existing.get();
        }

        TranscriptionJobEntity job = new TranscriptionJobEntity();
        job.setTaskId(taskId);
        job.setAudioUrl(audioUrl);
        job.setLanguageHint(languageHint);
        job.setEngine(engine);
        job.setModelVersion(modelVersion);
        job.setStatus(TranscriptionStatus.PENDING);

        try {
            return repository.save(job);
        } catch (DataIntegrityViolationException ex) {
            return repository.findByTaskId(taskId).orElseThrow(() -> ex);
        }
    }
}
