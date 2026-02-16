package com.oolshik.backend.transcription;

import com.oolshik.backend.config.TranscriptionAudioSourceProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Locale;

@Component
public class TranscriptionAudioSourceResolver {

    private static final Pattern LOCAL_AUDIO_STREAM_PATH =
            Pattern.compile("^/api/(?:public/)?media/audio/([^/]+)/stream/?$");

    private final TranscriptionAudioSourceProperties properties;

    public TranscriptionAudioSourceResolver(TranscriptionAudioSourceProperties properties) {
        this.properties = properties;
    }

    public String resolveForJob(String requestedVoiceUrl) {
        String normalized = normalize(requestedVoiceUrl);
        TranscriptionAudioSourceProperties.AudioSourceMode mode = properties.getAudioSourceMode();
        return switch (mode) {
            case REQUEST -> rewriteLocalUrlForWorker(normalized);
            case DEMO_FIXED -> requireConfiguredDemoUrl();
            case S3_ONLY -> requireS3Url(normalized);
        };
    }

    private String requireConfiguredDemoUrl() {
        String demo = normalize(properties.getDemoAudioUrl());
        if (demo == null) {
            throw new IllegalArgumentException("Demo transcription audio URL is not configured");
        }
        return demo;
    }

    private String requireS3Url(String normalized) {
        if (normalized == null) {
            return null;
        }
        URI uri = parseHttpUri(normalized);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("voiceUrl host is missing");
        }

        String hostLower = host.toLowerCase(Locale.ROOT);
        if (normalized.contains("/api/media/audio/") || normalized.contains("/api/public/media/audio/")) {
            throw new IllegalArgumentException("voiceUrl points to local stream endpoint; expected public S3 URL");
        }

        if (!matchesAnyAllowedHostSuffix(hostLower, properties.getS3AllowedHostSuffixes())) {
            throw new IllegalArgumentException("voiceUrl host is not allowed for S3-only mode: " + host);
        }
        return normalized;
    }

    private String rewriteLocalUrlForWorker(String normalized) {
        if (normalized == null || !properties.isRewriteLocalPublicStreamForWorker()) {
            return normalized;
        }

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            return normalized;
        }

        String path = uri.getPath();
        if (path == null) {
            return normalized;
        }

        Matcher matcher = LOCAL_AUDIO_STREAM_PATH.matcher(path);
        if (!matcher.matches()) {
            return normalized;
        }

        String audioId = matcher.group(1);
        String base = normalize(properties.getLocalWorkerBaseUrl());
        if (base == null) {
            return normalized;
        }
        return base.replaceAll("/+$", "") + "/api/public/media/audio/" + audioId + "/stream";
    }

    private static URI parseHttpUri(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("voiceUrl is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("voiceUrl must be an HTTPS URL in S3-only mode");
        }
        return uri;
    }

    private static boolean matchesAnyAllowedHostSuffix(String host, List<String> suffixes) {
        if (suffixes == null || suffixes.isEmpty()) {
            return false;
        }
        for (String suffix : suffixes) {
            String normalizedSuffix = normalize(suffix);
            if (normalizedSuffix == null) {
                continue;
            }
            String suffixLower = normalizedSuffix.toLowerCase(Locale.ROOT);
            if (host.endsWith(suffixLower)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
