package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * S3 兼容存储（MinIO / Cloudflare R2）原生分片上传辅助。
 */
public final class S3NativeMultipartSupport {

    /** 安装包分片大小（10MB）：270MB 约 27 片，避开 Compose 32 片上限 */
    public static final int INSTALLER_CHUNK_BYTES = 10 * 1024 * 1024;

    /** 建议浏览器并发上传分片数 */
    public static final int INSTALLER_UPLOAD_MAX_CONCURRENCY = 10;

    /** 预签名分片 URL 有效期（秒） */
    public static final int PRESIGN_PART_EXPIRY_SECONDS = 3600;

    private S3NativeMultipartSupport() {
    }

    public record UploadedPart(int partNumber, String etag) {
    }

    public static String presignUploadPart(
            MinioClient client,
            String bucket,
            String objectKey,
            String uploadId,
            int partNumber,
            int expirySeconds) throws Exception {
        Map<String, String> params = new HashMap<>(2);
        params.put("uploadId", uploadId);
        params.put("partNumber", String.valueOf(partNumber));
        return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(expirySeconds, TimeUnit.SECONDS)
                        .extraQueryParams(params)
                        .build());
    }
}
