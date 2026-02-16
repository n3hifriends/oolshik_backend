package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.transcription")
public class TranscriptionAudioSourceProperties {

    public enum AudioSourceMode {
        REQUEST,
        DEMO_FIXED,
        S3_ONLY
    }

    private AudioSourceMode audioSourceMode = AudioSourceMode.REQUEST;
    private String demoAudioUrl = "https://github.com/voxserv/audio_quality_testing_samples/raw/refs/heads/master/mono_44100/127389__acclivity__thetimehascome.wav";
    private List<String> s3AllowedHostSuffixes = new ArrayList<>(List.of(".amazonaws.com", ".cloudfront.net"));
    private boolean rewriteLocalPublicStreamForWorker = true;
    private String localWorkerBaseUrl = "http://api:8080";

    public AudioSourceMode getAudioSourceMode() {
        return audioSourceMode;
    }

    public void setAudioSourceMode(AudioSourceMode audioSourceMode) {
        this.audioSourceMode = audioSourceMode;
    }

    public String getDemoAudioUrl() {
        return demoAudioUrl;
    }

    public void setDemoAudioUrl(String demoAudioUrl) {
        this.demoAudioUrl = demoAudioUrl;
    }

    public List<String> getS3AllowedHostSuffixes() {
        return s3AllowedHostSuffixes;
    }

    public void setS3AllowedHostSuffixes(List<String> s3AllowedHostSuffixes) {
        this.s3AllowedHostSuffixes = s3AllowedHostSuffixes;
    }

    public boolean isRewriteLocalPublicStreamForWorker() {
        return rewriteLocalPublicStreamForWorker;
    }

    public void setRewriteLocalPublicStreamForWorker(boolean rewriteLocalPublicStreamForWorker) {
        this.rewriteLocalPublicStreamForWorker = rewriteLocalPublicStreamForWorker;
    }

    public String getLocalWorkerBaseUrl() {
        return localWorkerBaseUrl;
    }

    public void setLocalWorkerBaseUrl(String localWorkerBaseUrl) {
        this.localWorkerBaseUrl = localWorkerBaseUrl;
    }
}
