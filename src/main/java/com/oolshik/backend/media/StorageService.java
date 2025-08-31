package com.oolshik.backend.media;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {
    String writeTemp(String uploadId) throws IOException;
    void append(String tempKey, byte[] bytes) throws IOException;
    String finalizeUpload(String tempKey, String finalKey) throws IOException;
    InputStream readRange(String key, long start, long end) throws IOException;
    long size(String key) throws IOException;
    void delete(String key) throws IOException;
}