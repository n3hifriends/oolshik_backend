package com.oolshik.backend.media;

public class Dtos {
    public record InitUploadReq(String filename, String mimeType, long size, String requestId) {}
    public record InitUploadResp(String uploadId) {}
    public record CompleteUploadReq(String uploadId, int totalChunks, Long durationMs, Integer sampleRate) {}

    // MPU (Phase 2)
    public record MpuCreateReq(String filename, String mimeType) {}
    public record MpuCreateResp(String uploadId, String objectKey) {}
    public record MpuSignPartReq(String uploadId, String objectKey, int partNumber) {}
    public record MpuSignedPart(int partNumber, String url) {}
    public record MpuCompleteReq(String uploadId, String objectKey, java.util.List<PartETag> parts) {}
    public record PartETag(int partNumber, String eTag) {}
    public record MpuAbortReq(String uploadId, String objectKey) {}
}