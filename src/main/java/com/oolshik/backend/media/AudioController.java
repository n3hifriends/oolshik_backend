package com.oolshik.backend.media;

import com.oolshik.backend.media.Dtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;


//# Oolshik Audio Backend (Aligned & Phase-ready)
//
//- **Phase 1**: Server-buffered chunk upload -> finalize to Local or S3 (same API)
//- **Phase 2**: Optional client direct-to-S3 (presigned multipart) endpoints under `/mpu/*`
//- **Phase 3+**: Add transcription, retention, sharing without changing upload APIs
//
//## Endpoints
//- POST `/api/media/audio/init`, PUT `/api/media/audio/{uploadId}/chunk?index=N`, POST `/complete`
//- GET `/api/media/audio/my`, GET `/api/media/audio/{id}/stream` (Range), DELETE `/{id}`
//- POST `/api/media/audio/mpu/create|sign-part|complete|abort` (S3 only)

//## Security
//Protect `/api/media/**` for authenticated users (JWT). `auth.getName()` is used as `ownerUserId`.

@RestController
@RequestMapping("/api/media/audio")
public class AudioController {

    private final MultipartUploadService multipart;
    private final StorageService storage;
    private final AudioFileRepository repo;

    public AudioController(MultipartUploadService multipart,
                           StorageService storage,
                           AudioFileRepository repo) {
        this.multipart = multipart;
        this.storage = storage; // single bean named 'storageService'
        this.repo = repo;
    }

    // -------- Phase 1: Server-buffered chunk upload --------
    @PostMapping("/init")
    public InitUploadResp init(@RequestBody InitUploadReq req) throws IOException {
        String uploadId = multipart.initUpload();
        return new InitUploadResp(uploadId);
    }

    @PutMapping(value = "/{uploadId}/chunk", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void chunk(@PathVariable String uploadId, @RequestBody byte[] body) throws IOException {
        multipart.appendChunk(uploadId, body);
    }

    @Transactional
    @PostMapping("/complete")
    public AudioFile complete(@RequestBody CompleteUploadReq req, Authentication auth) throws IOException {
        String userId = auth.getName();
        String finalKey = userId + "/" + req.uploadId() + ".m4a";
        String storageKey = multipart.finalizeUpload(req.uploadId(), finalKey);

        AudioFile af = new AudioFile();
        af.setOwnerUserId(userId);
        af.setFilename(req.uploadId() + ".m4a");
        af.setMimeType("audio/m4a");
        af.setSizeBytes(storage.size(storageKey));
        af.setStorageKey(storageKey);
        af.setDurationMs(req.durationMs());
        af.setSampleRate(req.sampleRate());
        af.setRequestId(null);
        return repo.save(af);
    }

    @GetMapping("/my")
    public List<AudioFile> my(Authentication auth) {
        return repo.findByOwnerUserIdOrderByCreatedAtDesc(auth.getName());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id, Authentication auth) throws IOException {
        AudioFile af = repo.findById(id).orElseThrow();
        if (!af.getOwnerUserId().equals(auth.getName())) throw new RuntimeException("Forbidden");
        storage.delete(af.getStorageKey());
        repo.delete(af);
    }

    @GetMapping("/{id}/stream")
    public void stream(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response, Authentication auth) throws IOException {
        AudioFile af = repo.findById(id).orElseThrow();
        if (!af.getOwnerUserId().equals(auth.getName())) throw new RuntimeException("Forbidden");

        long fileLength = storage.size(af.getStorageKey());
        String range = request.getHeader("Range");
        long start = 0, end = fileLength - 1;
        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-");
            start = Long.parseLong(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
            if (end >= fileLength) end = fileLength - 1;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        long contentLength = end - start + 1;
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Type", af.getMimeType());
        response.setHeader("Content-Length", Long.toString(contentLength));
        if (range != null) response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);

        try (InputStream in = storage.readRange(af.getStorageKey(), start, end)) {
            in.transferTo(response.getOutputStream());
        }
    }

    // -------- Phase 2: Direct-to-S3 MPU (presigned) --------
    @PostMapping("/mpu/create")
    public MpuCreateResp mpuCreate(@RequestBody MpuCreateReq req, Authentication auth) {
        String userId = auth.getName();
        String objectKey = userId + "/mpu/" + UUID.randomUUID() + "-" + req.filename();
        if (!(storage instanceof S3StorageService s3)) throw new IllegalStateException("MPU requires S3 storage");
        var res = s3.mpuCreate(objectKey, req.mimeType());
        return new MpuCreateResp(res.uploadId(), objectKey);
    }

    @PostMapping("/mpu/sign-part")
    public MpuSignedPart mpuSign(@RequestBody MpuSignPartReq req) {
        if (!(storage instanceof S3StorageService s3)) throw new IllegalStateException("MPU requires S3 storage");
        String url = s3.presignPartUrl(req.uploadId(), req.objectKey(), req.partNumber(), Duration.ofMinutes(15));
        return new MpuSignedPart(req.partNumber(), url);
    }

    @PostMapping("/mpu/complete")
    public void mpuComplete(@RequestBody MpuCompleteReq req) {
        if (!(storage instanceof S3StorageService s3)) throw new IllegalStateException("MPU requires S3 storage");
        s3.mpuComplete(req.uploadId(), req.objectKey(), req.parts());
    }

    @PostMapping("/mpu/abort")
    public void mpuAbort(@RequestBody MpuAbortReq req) {
        if (!(storage instanceof S3StorageService s3)) throw new IllegalStateException("MPU requires S3 storage");
        s3.mpuAbort(req.uploadId(), req.objectKey());
    }
}