package com.oolshik.backend.media;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audio_files")
public class AudioFile {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String ownerUserId;
    @Column(nullable = false) private String filename;
    @Column(nullable = false) private String mimeType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private String storageKey;
    private String storageProvider;
    private String storageBucket;
    private String storageRegion;
    private String storageEndpoint;
    @Column(nullable = false) private boolean storagePathStyleAccessEnabled;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Long durationMs;
    private Integer sampleRate;
    @Column private String requestId;

    public UUID getId() { return id; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }
    public String getStorageRegion() { return storageRegion; }
    public void setStorageRegion(String storageRegion) { this.storageRegion = storageRegion; }
    public String getStorageEndpoint() { return storageEndpoint; }
    public void setStorageEndpoint(String storageEndpoint) { this.storageEndpoint = storageEndpoint; }
    public boolean isStoragePathStyleAccessEnabled() { return storagePathStyleAccessEnabled; }
    public void setStoragePathStyleAccessEnabled(boolean storagePathStyleAccessEnabled) { this.storagePathStyleAccessEnabled = storagePathStyleAccessEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Integer getSampleRate() { return sampleRate; }
    public void setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
