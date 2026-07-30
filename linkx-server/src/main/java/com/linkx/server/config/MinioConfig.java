package com.linkx.server.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final LinkxProperties linkxProperties;

    @Bean
    public MinioClient minioClient() {
        LinkxProperties.Minio minioProps = linkxProperties.getMinio();

        if (minioProps.getAccessKey() == null || minioProps.getAccessKey().isBlank()) {
            throw new IllegalStateException(
                    "MINIO_ACCESS_KEY 未配置，请在 .env.local 或环境变量中设置（勿使用默认 minioadmin）");
        }
        if (minioProps.getSecretKey() == null || minioProps.getSecretKey().isBlank()) {
            throw new IllegalStateException(
                    "MINIO_SECRET_KEY 未配置，请在 .env.local 或环境变量中设置（勿使用默认 minioadmin123）");
        }

        // 显式超时，避免默认无限等待在网络抖动时拖垮业务线程
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build();

        MinioClient client = MinioClient.builder()
                .endpoint(minioProps.getEndpoint())
                .credentials(minioProps.getAccessKey(), minioProps.getSecretKey())
                .httpClient(httpClient)
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
                    "MinIO 连接失败 (endpoint={}, bucket={}): {}",
                    minioProps.getEndpoint(),
                    minioProps.getBucketName(),
                    e.getMessage()
            );
            throw new IllegalStateException(
                    "MinIO 连接失败 (endpoint=" + minioProps.getEndpoint()
                            + ", bucket=" + minioProps.getBucketName()
                            + ")，启动中断以避免带病运行", e);
        }
        
        return client;
    }
}
