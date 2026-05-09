package com.oolshik.backend.media;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioPlaybackUrlResolverTest {

    @Test
    void localStorageUsesBackendStreamUrl() throws Exception {
        AudioPlaybackUrlResolver resolver = new AudioPlaybackUrlResolver(mock(StorageService.class), mock(AudioFileRepository.class));
        AudioFile audioFile = audioFile("store/user/file.m4a");

        assertThat(resolver.resolve(audioFile))
                .isEqualTo("/api/media/audio/" + audioFile.getId() + "/stream");
    }

    @Test
    void s3StorageUsesResolvedDownloadUrl() throws Exception {
        S3StorageService storage = mock(S3StorageService.class);
        AudioPlaybackUrlResolver resolver = new AudioPlaybackUrlResolver(storage, mock(AudioFileRepository.class));
        AudioFile audioFile = audioFile("audio/user/file.m4a");

        when(storage.resolveDownloadUrl(audioFile.getStorageKey()))
                .thenReturn(Optional.of("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a?sig=1"));

        assertThat(resolver.resolve(audioFile))
                .isEqualTo("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a?sig=1");
    }

    @Test
    void s3StorageFallsBackToPublicUrlWhenPresignUnavailable() throws Exception {
        S3StorageService storage = mock(S3StorageService.class);
        AudioPlaybackUrlResolver resolver = new AudioPlaybackUrlResolver(storage, mock(AudioFileRepository.class));
        AudioFile audioFile = audioFile("audio/user/file.m4a");

        when(storage.resolveDownloadUrl(audioFile.getStorageKey()))
                .thenReturn(Optional.empty());
        when(storage.toPublicUrl(audioFile.getStorageKey()))
                .thenReturn("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a");

        assertThat(resolver.resolve(audioFile))
                .isEqualTo("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a");
    }

    @Test
    void resolveStoredUrlConvertsLocalVoiceUrlToResolvedPlaybackUrl() throws Exception {
        S3StorageService storage = mock(S3StorageService.class);
        AudioFileRepository repo = mock(AudioFileRepository.class);
        AudioPlaybackUrlResolver resolver = new AudioPlaybackUrlResolver(storage, repo);
        AudioFile audioFile = audioFile("audio/user/file.m4a");
        String localUrl = "https://api.example.com/api/media/audio/" + audioFile.getId() + "/stream";

        when(repo.findById(audioFile.getId())).thenReturn(Optional.of(audioFile));
        when(storage.resolveDownloadUrl(audioFile.getStorageKey()))
                .thenReturn(Optional.of("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a?sig=1"));

        assertThat(resolver.resolveStoredUrl(localUrl))
                .isEqualTo("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a?sig=1");
    }

    @Test
    void resolveStoredUrlLeavesRemoteUrlUntouched() {
        AudioPlaybackUrlResolver resolver = new AudioPlaybackUrlResolver(mock(StorageService.class), mock(AudioFileRepository.class));

        assertThat(resolver.resolveStoredUrl("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a"))
                .isEqualTo("https://bucket.s3.ap-south-1.amazonaws.com/audio/user/file.m4a");
    }

    private static AudioFile audioFile(String storageKey) throws Exception {
        AudioFile audioFile = new AudioFile();
        audioFile.setStorageKey(storageKey);
        Field id = AudioFile.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(audioFile, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return audioFile;
    }
}
