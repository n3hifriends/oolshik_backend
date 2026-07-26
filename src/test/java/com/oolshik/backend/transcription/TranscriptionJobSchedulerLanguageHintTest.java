package com.oolshik.backend.transcription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Republishing a PENDING job must NOT mutate its persisted languageHint -- a one-time
 * Flyway migration (V51) corrects historical stale hints once; permanently overwriting on
 * every scheduled republish would discard a legitimate explicit hint (e.g. from a future
 * per-recording language picker), contradicting stt-worker/README.md's documented contract.
 */
@ExtendWith(MockitoExtension.class)
class TranscriptionJobSchedulerLanguageHintTest {

    @Mock private TranscriptionJobRepository repository;
    @Mock private TranscriptionJobPublisher publisher;

    private TranscriptionJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TranscriptionJobScheduler(repository, publisher);
    }

    @Test
    void republishDoesNotMutateAnExplicitLanguageHint() {
        TranscriptionJobEntity job = new TranscriptionJobEntity();
        job.setJobId(UUID.randomUUID());
        job.setTaskId(UUID.randomUUID());
        job.setStatus(TranscriptionStatus.PENDING);
        job.setLanguageHint("ta-IN");

        when(repository.findTop50ByStatusOrderByUpdatedAtAsc(TranscriptionStatus.PENDING))
                .thenReturn(List.of(job));

        scheduler.republishPendingJobs();

        assertEquals("ta-IN", job.getLanguageHint());
        assertEquals(TranscriptionStatus.PENDING, job.getStatus());
        verify(publisher).publishJob(job);
        verify(repository, never()).save(job);
        verify(repository, never()).saveAndFlush(job);
    }
}
