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
            // 默认新建桶即为私有；若已挂载公开策略则告警（应用依赖预签名，不应匿名可读）
            try {
                String policy = client.getBucketPolicy(
                        io.minio.GetBucketPolicyArgs.builder().bucket(bucketName).build());
                if (policy != null && !policy.isBlank()
                        && policy.contains("\"Effect\":\"Allow\"")
                        && policy.contains("\"Principal\"")
                        && policy.contains("\"*\"")) {
                    log.warn("MinIO bucket [{}] 可能存在公开读策略，请确认仅预签名可访问", bucketName);
                } else {
                    log.info("MinIO bucket [{}] 策略检查通过（私有/无公开读）", bucketName);
                }
            } catch (Exception policyEx) {
                log.debug("读取 MinIO bucket policy 跳过: {}", policyEx.getMessage());
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
