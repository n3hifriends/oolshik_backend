package com.oolshik.backend.media;

import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class S3StorageService implements StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String prefix;
    private final Path tmpRoot;

    public S3StorageService(String bucket, String region, String prefix, String endpoint, boolean pathStyle) throws IOException {
        this.bucket = bucket;
        this.prefix = prefix != null ? prefix : "audio/";
        var base = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());
        var pre = S3Presigner.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create());
        if (StringUtils.hasText(endpoint)) {
            var uri = URI.create(endpoint);
            base = base.endpointOverride(uri);
            pre = pre.endpointOverride(uri);
        }
        if (pathStyle) {
            base = base.serviceConfiguration(cfg -> cfg.pathStyleAccessEnabled(true));
        }
        this.s3 = base.build();
        this.presigner = pre.build();
        this.tmpRoot = Paths.get("./data/audio/tmp-s3").toAbsolutePath();
        Files.createDirectories(tmpRoot);
    }

    private String key(String finalKey) {
        String k = (prefix.endsWith("/") ? prefix : prefix + "/") + finalKey;
        return k.replace("//", "/");
    }

    @Override
    public String writeTemp(String uploadId) throws IOException {
        Path p = tmpRoot.resolve(uploadId + ".part");
        Files.deleteIfExists(p);
        Files.createFile(p);
        return p.toString();
    }

    @Override
    public void append(String tempKey, byte[] bytes) throws IOException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempKey, true))) {
            out.write(bytes);
        }
    }

    @Override
    public String finalizeUpload(String tempKey, String finalKey) throws IOException {
        Path src = Paths.get(tempKey);
        String s3key = key(finalKey);
        try (InputStream in = new BufferedInputStream(new FileInputStream(src.toFile()))) {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3key)
                    .contentType("audio/m4a")
                    .build();
            s3.putObject(req, RequestBody.fromInputStream(in, Files.size(src)));
        } catch (S3Exception e) {
            throw new IOException("S3 putObject failed: " + e.awsErrorDetails().errorMessage(), e);
        } finally {
            Files.deleteIfExists(src);
        }
        return s3key;
    }

    @Override
    public InputStream readRange(String key, long start, long end) throws IOException {
        String range = "bytes=" + start + "-" + end;
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range(range)
                .build();
        try {
            ResponseInputStream<GetObjectResponse> in = s3.getObject(req);
            return in;
        } catch (S3Exception e) {
            throw new IOException("S3 getObject failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public long size(String key) throws IOException {
        HeadObjectRequest req = HeadObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            HeadObjectResponse head = s3.headObject(req);
            return head.contentLength();
        } catch (S3Exception e) {
            throw new IOException("S3 headObject failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            throw new IOException("S3 deleteObject failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    // ---- Presigned MPU helpers (Phase 2) ----
    public CreateMultipartUploadResponse mpuCreate(String objectKey, String mimeType) {
        CreateMultipartUploadRequest req = CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(objectKey).contentType(mimeType).build();
        return s3.createMultipartUpload(req);
    }

    public String presignPartUrl(String uploadId, String objectKey, int partNumber, Duration ttl) {
        UploadPartRequest upr = UploadPartRequest.builder()
                .bucket(bucket).key(objectKey).uploadId(uploadId).partNumber(partNumber).build();
        PresignedUploadPartRequest p = presigner.presignUploadPart(b -> b
                .signatureDuration(ttl).uploadPartRequest(upr));
        return p.url().toString();
    }

    public void mpuComplete(String uploadId, String objectKey, java.util.List<Dtos.PartETag> parts) {
        CompletedMultipartUpload cmp = CompletedMultipartUpload.builder().parts(
                parts.stream()
                        .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build())
                        .toList()
        ).build();
        CompleteMultipartUploadRequest req = CompleteMultipartUploadRequest.builder()
                .bucket(bucket).key(objectKey).uploadId(uploadId).multipartUpload(cmp).build();
        s3.completeMultipartUpload(req);
    }

    public void mpuAbort(String uploadId, String objectKey) {
        AbortMultipartUploadRequest req = AbortMultipartUploadRequest.builder()
                .bucket(bucket).key(objectKey).uploadId(uploadId).build();
        s3.abortMultipartUpload(req);
    }
}