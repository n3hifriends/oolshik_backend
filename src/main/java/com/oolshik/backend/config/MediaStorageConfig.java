package com.oolshik.backend.config;

import com.oolshik.backend.media.LocalStorageService;
import com.oolshik.backend.media.S3StorageService;
import com.oolshik.backend.media.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaStorageConfig {

    @Bean // name: storageService
    @ConditionalOnProperty(name = "media.storage", havingValue = "local", matchIfMissing = true)
    public StorageService storageServiceLocal(
            @Value("${media.local.root:./data/audio}") String root
    ) throws Exception {
        return new LocalStorageService(root);
    }

    @Bean // name: storageService
    @ConditionalOnProperty(name = "media.storage", havingValue = "s3")
    public StorageService storageServiceS3(
            @Value("${media.s3.bucket}") String bucket,
            @Value("${media.s3.region}") String region,
            @Value("${media.s3.prefix:audio/}") String prefix,
            @Value("${media.s3.endpoint:}") String endpoint,
            @Value("${media.s3.pathStyleAccessEnabled:false}") boolean pathStyle
    ) throws Exception {
        return new S3StorageService(bucket, region, prefix, endpoint, pathStyle);
    }
}