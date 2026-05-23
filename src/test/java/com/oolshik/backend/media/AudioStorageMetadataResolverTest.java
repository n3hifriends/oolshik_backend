package com.oolshik.backend.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioStorageMetadataResolverTest {

    @Test
    void populateInfersS3MetadataForFreshAudioFile() {
        S3StorageService s3 = mock(S3StorageService.class);
        AudioStorageMetadataResolver resolver = new AudioStorageMetadataResolver(s3);
        AudioFile audioFile = new AudioFile();
        audioFile.setStorageKey("user/direct/file.m4a");

        when(s3.describe("user/direct/file.m4a"))
                .thenReturn(new AudioStorageMetadataResolver.StorageReference(
                        "AWS",
                        "bucket-name",
                        "user/direct/file.m4a",
                        "ap-south-1",
                        null,
                        false
                ));

        resolver.populate(audioFile);

        assertThat(audioFile.getStorageProvider()).isEqualTo("AWS");
        assertThat(audioFile.getStorageBucket()).isEqualTo("bucket-name");
        assertThat(audioFile.getStorageRegion()).isEqualTo("ap-south-1");
    }

    @Test
    void resolveIgnoresLegacyLocalMarkerForNonLocalS3Key() {
        S3StorageService s3 = mock(S3StorageService.class);
        AudioStorageMetadataResolver resolver = new AudioStorageMetadataResolver(s3);
        AudioFile audioFile = new AudioFile();
        audioFile.setStorageProvider("LOCAL");
        audioFile.setStorageKey("user/direct/file.m4a");

        AudioStorageMetadataResolver.StorageReference expected =
                new AudioStorageMetadataResolver.StorageReference(
                        "AWS",
                        "bucket-name",
                        "user/direct/file.m4a",
                        "ap-south-1",
                        null,
                        false
                );
        when(s3.describe("user/direct/file.m4a")).thenReturn(expected);

        assertThat(resolver.resolve(audioFile)).isEqualTo(expected);
    }
}
