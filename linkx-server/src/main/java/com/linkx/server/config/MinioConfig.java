package com.linkx.server.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final LinkxProperties linkxProperties;

    @Bean
    public MinioClient minioClient() {
        LinkxProperties.Minio minioProps = linkxProperties.getMinio();
        
        MinioClient client = MinioClient.builder()
                .endpoint(minioProps.getEndpoint())
                .credentials(minioProps.getAccessKey(), minioProps.getSecretKey())
                .build();
        
        // 启动时检查并创建 bucket
        try {
            String bucketName = minioProps.getBucketName();
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error(
                    "MinIO 连接失败 (endpoint={}, accessKey={}, bucket={}): {}",
                    minioProps.getEndpoint(),
                    minioProps.getAccessKey(),
                    minioProps.getBucketName(),
                    e.getMessage()
            );
        }
        
        return client;
    }
}
