// src/main/java/com/oolshik/backend/media/MediaPresignController.java
package com.oolshik.backend.media;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaPresignController {

    private final StorageService storage;
    private final AudioFileRegistrationService registrationService;

    public MediaPresignController(StorageService storage,
                                  AudioFileRegistrationService registrationService) {
        this.storage = storage;
        this.registrationService = registrationService;
    }

    public record PresignReq(String contentType) {}
    public record PresignResp(String uploadUrl, String fileUrl, String downloadUrl, String objectKey) {}
    public record DirectUploadCompleteReq(String objectKey, String filename, String mimeType, Long durationMs, Integer sampleRate) {}

    @PostMapping("/pre-signed")
    public ResponseEntity<?> create(@RequestBody PresignReq req, Authentication auth) {
        if (!(storage instanceof S3StorageService s3)) {
            // In local/dev mode we don’t support presigned PUT. Frontend should fall back to chunked upload.
            return ResponseEntity.status(501).body(Map.of("error", "presign_unsupported"));
        }
        String userId = auth.getName();
        String ext = guessExt(req.contentType());
        String objectKey = userId + "/direct/" + UUID.randomUUID() + ext;
        String uploadUrl = s3.presignPutUrl(objectKey, req.contentType(), Duration.ofMinutes(15));
        String fileUrl = s3.toPublicUrl(objectKey);
        String downloadUrl = s3.resolveDownloadUrl(objectKey).orElse(fileUrl);
        return ResponseEntity.ok(new PresignResp(uploadUrl, fileUrl, downloadUrl, objectKey));
    }

    @PostMapping("/pre-signed/complete")
    public ResponseEntity<?> complete(@RequestBody DirectUploadCompleteReq req, Authentication auth) throws java.io.IOException {
        if (!(storage instanceof S3StorageService)) {
            return ResponseEntity.status(501).body(Map.of("error", "presign_unsupported"));
        }
        String userId = auth.getName();
        String objectKey = req.objectKey() == null ? null : req.objectKey().trim();
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(userId + "/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_object_key"));
        }
        String filename = (req.filename() == null || req.filename().isBlank())
                ? objectKey.substring(objectKey.lastIndexOf('/') + 1)
                : req.filename().trim();
        String mimeType = (req.mimeType() == null || req.mimeType().isBlank()) ? "application/octet-stream" : req.mimeType().trim();
        AudioFile audioFile = registrationService.register(
                userId,
                filename,
                mimeType,
                objectKey,
                req.durationMs(),
                req.sampleRate(),
                null
        );
        return ResponseEntity.ok(audioFile);
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
