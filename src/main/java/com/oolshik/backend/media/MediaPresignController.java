// src/main/java/com/oolshik/backend/media/MediaPresignController.java
package com.oolshik.backend.media;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaPresignController {

    private final StorageService storage;

    public MediaPresignController(StorageService storage) {
        this.storage = storage;
    }

    public record PresignReq(String contentType) {}
    public record PresignResp(String uploadUrl, String fileUrl, String objectKey) {}

    @PostMapping("/pre-signed")
    public PresignResp create(@RequestBody PresignReq req, Authentication auth) {
        if (!(storage instanceof S3StorageService s3)) {
            // In local/dev mode we don’t support presigned PUT. Frontend should fall back to chunked upload.
            throw new UnsupportedOperationException("Presigned uploads require media.storage=s3");
        }
        String userId = auth.getName();
        String ext = guessExt(req.contentType());
        String objectKey = userId + "/direct/" + UUID.randomUUID() + ext;
        String uploadUrl = s3.presignPutUrl(objectKey, req.contentType(), Duration.ofMinutes(15));
        String fileUrl = s3.toPublicUrl(objectKey);
        return new PresignResp(uploadUrl, fileUrl, objectKey);
    }

    private static String guessExt(String ct) {
        if (ct == null) return ".bin";
        String c = ct.toLowerCase();
        if (c.contains("audio/m4a") || c.contains("audio/aac")) return ".m4a";
        if (c.contains("audio/mp3") || c.contains("mpeg")) return ".mp3";
        if (c.contains("audio/ogg") || c.contains("opus")) return ".ogg";
        if (c.contains("wav")) return ".wav";
        return ".bin";
    }
}