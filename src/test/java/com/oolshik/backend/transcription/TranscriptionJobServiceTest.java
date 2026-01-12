package com.oolshik.backend.transcription;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TranscriptionJobServiceTest {

    @Test
    void createOrGetReturnsExistingJob() {
        TranscriptionJobRepository repo = mock(TranscriptionJobRepository.class);
        TranscriptionJobService service = new TranscriptionJobService(repo);

        UUID taskId = UUID.randomUUID();
        TranscriptionJobEntity existing = new TranscriptionJobEntity();
        existing.setJobId(UUID.randomUUID());
        existing.setTaskId(taskId);
        existing.setAudioUrl("https://example.com/audio.m4a");
        existing.setEngine("faster-whisper");
        existing.setModelVersion("small");

        when(repo.findByTaskId(taskId)).thenReturn(Optional.of(existing));

        TranscriptionJobEntity result = service.createOrGet(taskId, "https://example.com/audio.m4a", null,
                "faster-whisper", "small");

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void createOrGetCreatesNewJob() {
        TranscriptionJobRepository repo = mock(TranscriptionJobRepository.class);
        TranscriptionJobService service = new TranscriptionJobService(repo);

        UUID taskId = UUID.randomUUID();
        when(repo.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(repo.save(any(TranscriptionJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TranscriptionJobEntity result = service.createOrGet(taskId, "https://example.com/audio.m4a", "hi-IN",
                "faster-whisper", "small");

        ArgumentCaptor<TranscriptionJobEntity> captor = ArgumentCaptor.forClass(TranscriptionJobEntity.class);
        verify(repo).save(captor.capture());

        TranscriptionJobEntity saved = captor.getValue();
        assertThat(saved.getTaskId()).isEqualTo(taskId);
        assertThat(saved.getAudioUrl()).isEqualTo("https://example.com/audio.m4a");
        assertThat(saved.getLanguageHint()).isEqualTo("hi-IN");
        assertThat(saved.getEngine()).isEqualTo("faster-whisper");
        assertThat(saved.getModelVersion()).isEqualTo("small");
        assertThat(result.getTaskId()).isEqualTo(taskId);
    }
}
