package com.oolshik.backend.media;

import org.springframework.stereotype.Component;

@Component
public class AudioStorageMetadataResolver {

    private final StorageService storage;

    public AudioStorageMetadataResolver(StorageService storage) {
        this.storage = storage;
    }

    public record StorageReference(
            String provider,
            String bucket,
            String objectKey,
            String region,
            String endpoint,
            boolean pathStyleAccessEnabled
    ) {
        public boolean isObjectStorage() {
            return bucket != null && objectKey != null && !"LOCAL".equalsIgnoreCase(provider);
        }
    }

    public void populate(AudioFile audioFile) {
        StorageReference ref = resolve(audioFile);
        audioFile.setStorageProvider(ref.provider());
        audioFile.setStorageBucket(ref.bucket());
        audioFile.setStorageRegion(ref.region());
        audioFile.setStorageEndpoint(ref.endpoint());
        audioFile.setStoragePathStyleAccessEnabled(ref.pathStyleAccessEnabled());
    }

    public StorageReference resolve(AudioFile audioFile) {
        if (audioFile == null) {
            return null;
        }
        if (shouldUseStoredMetadata(audioFile)) {
            return new StorageReference(
                    audioFile.getStorageProvider(),
                    audioFile.getStorageBucket(),
                    audioFile.getStorageKey(),
                    audioFile.getStorageRegion(),
                    audioFile.getStorageEndpoint(),
                    audioFile.isStoragePathStyleAccessEnabled()
            );
        }
        if (storage instanceof S3StorageService s3) {
            return s3.describe(audioFile.getStorageKey());
        }
        return new StorageReference("LOCAL", null, audioFile.getStorageKey(), null, null, false);
    }

    private boolean shouldUseStoredMetadata(AudioFile audioFile) {
        String provider = audioFile.getStorageProvider();
        if (provider == null || provider.isBlank()) {
            return false;
        }
        if ("LOCAL".equalsIgnoreCase(provider)) {
            return audioFile.getStorageBucket() != null
                    || looksLikeLocalStorageKey(audioFile.getStorageKey())
                    || !(storage instanceof S3StorageService);
        }
        return audioFile.getStorageBucket() != null || !(storage instanceof S3StorageService);
    }

    private boolean looksLikeLocalStorageKey(String storageKey) {
        if (storageKey == null) {
            return false;
        }
        return storageKey.startsWith("store/") || storageKey.startsWith("tmp/");
    }
}
