package com.oolshik.backend.transcription;

public interface TranscriptionJobPublisher {
    void publishJob(TranscriptionJobEntity job);
}
