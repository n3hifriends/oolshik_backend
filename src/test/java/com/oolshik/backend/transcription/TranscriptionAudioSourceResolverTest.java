package com.oolshik.backend.transcription;

import com.oolshik.backend.config.TranscriptionAudioSourceProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionAudioSourceResolverTest {

    @Test
    void requestModeUsesRequestedUrl() {
        TranscriptionAudioSourceProperties props = properties(TranscriptionAudioSourceProperties.AudioSourceMode.REQUEST);
        props.setRewriteLocalPublicStreamForWorker(false);
        TranscriptionAudioSourceResolver resolver = new TranscriptionAudioSourceResolver(props);

        assertThat(resolver.resolveForJob(" https://bucket.s3.ap-south-1.amazonaws.com/a.m4a "))
                .isEqualTo("https://bucket.s3.ap-south-1.amazonaws.com/a.m4a");
    }

    @Test
    void requestModeRewritesLocalStreamToWorkerBase() {
        TranscriptionAudioSourceProperties props = properties(TranscriptionAudioSourceProperties.AudioSourceMode.REQUEST);
        props.setRewriteLocalPublicStreamForWorker(true);
        props.setLocalWorkerBaseUrl("http://api:8080");
        TranscriptionAudioSourceResolver resolver = new TranscriptionAudioSourceResolver(props);

        assertThat(resolver.resolveForJob("http://10.0.2.2:8080/api/public/media/audio/72da3149-7b0a-416b-9739-bdc6e6bb1914/stream"))
                .isEqualTo("http://api:8080/api/public/media/audio/72da3149-7b0a-416b-9739-bdc6e6bb1914/stream");
    }

    @Test
    void demoFixedModeAlwaysUsesConfiguredUrl() {
        TranscriptionAudioSourceProperties props = properties(TranscriptionAudioSourceProperties.AudioSourceMode.DEMO_FIXED);
        props.setDemoAudioUrl("https://demo.local/audio.wav");
        TranscriptionAudioSourceResolver resolver = new TranscriptionAudioSourceResolver(props);

        assertThat(resolver.resolveForJob("https://bucket.s3.ap-south-1.amazonaws.com/a.m4a"))
                .isEqualTo("https://demo.local/audio.wav");
    }

    @Test
    void s3OnlyModeAcceptsAllowedHostSuffix() {
        TranscriptionAudioSourceProperties props = properties(TranscriptionAudioSourceProperties.AudioSourceMode.S3_ONLY);
        props.setS3AllowedHostSuffixes(List.of(".amazonaws.com"));
        TranscriptionAudioSourceResolver resolver = new TranscriptionAudioSourceResolver(props);

        assertThat(resolver.resolveForJob("https://bucket.s3.ap-south-1.amazonaws.com/a.m4a"))
                .isEqualTo("https://bucket.s3.ap-south-1.amazonaws.com/a.m4a");
    }

    @Test
    void s3OnlyModeRejectsLocalStreamUrl() {
        TranscriptionAudioSourceProperties props = properties(TranscriptionAudioSourceProperties.AudioSourceMode.S3_ONLY);
        TranscriptionAudioSourceResolver resolver = new TranscriptionAudioSourceResolver(props);

        assertThatThrownBy(() -> resolver.resolveForJob("https://api.example.com/api/media/audio/abc/stream"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local stream endpoint");
    }

    @Test
    void s3OnlyModeRejectsPublicLocalStreamUrl() {
        TranscriptionAudioSourceProperties props = properties(TranscriptionAudioSourceProperties.AudioSourceMode.S3_ONLY);
        TranscriptionAudioSourceResolver resolver = new TranscriptionAudioSourceResolver(props);

        assertThatThrownBy(() -> resolver.resolveForJob("https://api.example.com/api/public/media/audio/abc/stream"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local stream endpoint");
    }

    @Test
    void s3OnlyModeRejectsUnexpectedHost() {
        TranscriptionAudioSourceProperties props = properties(TranscriptionAudioSourceProperties.AudioSourceMode.S3_ONLY);
        props.setS3AllowedHostSuffixes(List.of(".amazonaws.com"));
        TranscriptionAudioSourceResolver resolver = new TranscriptionAudioSourceResolver(props);

        assertThatThrownBy(() -> resolver.resolveForJob("https://example.com/audio.m4a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    private static TranscriptionAudioSourceProperties properties(TranscriptionAudioSourceProperties.AudioSourceMode mode) {
        TranscriptionAudioSourceProperties props = new TranscriptionAudioSourceProperties();
        props.setAudioSourceMode(mode);
        return props;
    }
}
