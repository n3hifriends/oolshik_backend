package com.oolshik.backend.media;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AudioPlaybackUrlResolver {

    private static final Pattern LOCAL_AUDIO_STREAM_PATH =
            Pattern.compile("^/api/(?:public/)?media/audio/([0-9a-fA-F-]{36})/stream/?$");

    private final StorageService storage;
    private final AudioFileRepository audioRepo;

    public AudioPlaybackUrlResolver(StorageService storage, AudioFileRepository audioRepo) {
        this.storage = storage;
        this.audioRepo = audioRepo;
    }

    public String resolve(AudioFile audioFile) {
        if (audioFile == null) {
            return null;
        }
        if (storage instanceof S3StorageService s3) {
            return s3.resolveDownloadUrl(audioFile.getStorageKey())
                    .orElseGet(() -> s3.toPublicUrl(audioFile.getStorageKey()));
        }
        return "/api/media/audio/" + audioFile.getId() + "/stream";
    }

    public String resolve(UUID audioFileId, String storedUrl) {
        if (audioFileId != null) {
            return audioRepo.findById(audioFileId)
                    .map(this::resolve)
                    .orElseGet(() -> resolveStoredUrl(storedUrl));
        }
        return resolveStoredUrl(storedUrl);
    }

    public String resolveStoredUrl(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return storedUrl;
        }

        String trimmed = storedUrl.trim();
        String path;
        try {
            path = URI.create(trimmed).getPath();
        } catch (IllegalArgumentException ex) {
            return trimmed;
        }
        if (path == null) {
            return trimmed;
        }

        Matcher matcher = LOCAL_AUDIO_STREAM_PATH.matcher(path);
        if (!matcher.matches()) {
            return trimmed;
        }

        try {
            UUID audioId = UUID.fromString(matcher.group(1));
            return audioRepo.findById(audioId)
                    .map(this::resolve)
                    .orElse(trimmed);
        } catch (IllegalArgumentException ex) {
            return trimmed;
        }
    }
}
