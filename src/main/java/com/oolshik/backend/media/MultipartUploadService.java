package com.oolshik.backend.media;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MultipartUploadService {

    private final StorageService storage;
    private final Map<String, String> tempKeyByUploadId = new ConcurrentHashMap<>();

    public MultipartUploadService(StorageService storage) {
        this.storage = storage;
    }

    public String initUpload() throws IOException {
        String uploadId = UUID.randomUUID().toString();
        String tempKey = storage.writeTemp(uploadId);
        tempKeyByUploadId.put(uploadId, tempKey);
        return uploadId;
    }

    public void appendChunk(String uploadId, byte[] bytes) throws IOException {
        String tempKey = tempKeyByUploadId.get(uploadId);
        if (tempKey == null) throw new IllegalStateException("Unknown uploadId");
        synchronized (tempKey.intern()) {
            storage.append(tempKey, bytes);
        }
    }

    public String finalizeUpload(String uploadId, String finalKey) throws IOException {
        String tempKey = tempKeyByUploadId.remove(uploadId);
        if (tempKey == null) throw new IllegalStateException("Unknown uploadId");
        return storage.finalizeUpload(tempKey, finalKey);
    }
}