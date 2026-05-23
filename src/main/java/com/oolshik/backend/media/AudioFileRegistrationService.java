package com.oolshik.backend.media;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
public class AudioFileRegistrationService {

    private final StorageService storage;
    private final AudioFileRepository repo;
    private final AudioStorageMetadataResolver metadataResolver;

    public AudioFileRegistrationService(StorageService storage,
                                        AudioFileRepository repo,
                                        AudioStorageMetadataResolver metadataResolver) {
        this.storage = storage;
        this.repo = repo;
        this.metadataResolver = metadataResolver;
    }

    @Transactional
    public AudioFile register(String ownerUserId,
                              String filename,
                              String mimeType,
                              String storageKey,
                              Long durationMs,
                              Integer sampleRate,
                              String requestId) throws IOException {
        AudioFile af = new AudioFile();
        af.setOwnerUserId(ownerUserId);
        af.setFilename(filename);
        af.setMimeType(mimeType);
        af.setSizeBytes(storage.size(storageKey));
        af.setStorageKey(storageKey);
        af.setDurationMs(durationMs);
        af.setSampleRate(sampleRate);
        af.setRequestId(requestId);
        metadataResolver.populate(af);
        return repo.save(af);
    }
}
