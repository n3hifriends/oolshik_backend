package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "media.s3")
public class MediaS3Properties {

    public enum Provider {
        AWS,
        DIGITALOCEAN
    }

    public enum DownloadUrlMode {
        NONE,
        PUBLIC,
        PRESIGNED_GET
    }

    private Provider provider = Provider.AWS;
    private String bucket;
    private String region = "ap-south-1";
    private String prefix = "audio/";
    private String endpoint;
    private boolean pathStyleAccessEnabled;
    private DownloadUrlMode downloadUrlMode = DownloadUrlMode.PRESIGNED_GET;
    private String publicBaseUrl;
    private long presignedGetTtlSeconds = 900;

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isPathStyleAccessEnabled() {
        return pathStyleAccessEnabled;
    }

    public void setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
        this.pathStyleAccessEnabled = pathStyleAccessEnabled;
    }

    public DownloadUrlMode getDownloadUrlMode() {
        return downloadUrlMode;
    }

    public void setDownloadUrlMode(DownloadUrlMode downloadUrlMode) {
        this.downloadUrlMode = downloadUrlMode;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getPresignedGetTtlSeconds() {
        return presignedGetTtlSeconds;
    }

    public void setPresignedGetTtlSeconds(long presignedGetTtlSeconds) {
        this.presignedGetTtlSeconds = presignedGetTtlSeconds;
    }
}
