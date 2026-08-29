package com.linkx.server.storage.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.storage.DirectMultipartCapableBackend;
import com.linkx.server.storage.ObjectStorageBackend;
import com.linkx.server.storage.S3NativeMultipartSupport;
import com.linkx.server.storage.StorageProviderType;
import com.linkx.server.storage.ExtendedMinioAsyncClient;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MinioObjectStorageBackend implements ObjectStorageBackend, DirectMultipartCapableBackend {

    private final LinkxProperties linkxProperties;
    private volatile MinioClient client;
    private volatile ExtendedMinioAsyncClient asyncClient;

    public MinioObjectStorageBackend(LinkxProperties linkxProperties) {
        this.linkxProperties = linkxProperties;
    }

    @Override
    public StorageProviderType providerType() {
        return StorageProviderType.MINIO;
    }

    @Override
    public void testConnection() throws Exception {
        MinioClient c = client();
        String bucket = bucketName();
        boolean exists = c.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            throw new IllegalStateException("MinIO 桶不存在: " + bucket);
        }
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long size, String contentType) throws Exception {
        client().putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName())
                        .object(objectKey)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    }

    @Override
    public void deleteObject(String objectKey) throws Exception {
        client().removeObject(
                RemoveObjectArgs.builder().bucket(bucketName()).object(objectKey).build()
        );
    }

    @Override
    public String presignGet(String objectKey, int expirySeconds) {
        try {
            return client().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName())
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO 预签名失败: key={}, err={}", objectKey, e.getMessage());
            return null;
        }
    }

    @Override
    public FileStorageService.StoredObject open(String objectKey) throws Exception {
        StatObjectResponse stat = client().statObject(
                StatObjectArgs.builder().bucket(bucketName()).object(objectKey).build()
        );
        InputStream stream = client().getObject(
                GetObjectArgs.builder().bucket(bucketName()).object(objectKey).build()
        );
        String contentType = stat.contentType();
        if (!StringUtils.hasText(contentType)) {
            contentType = "application/octet-stream";
        }
        return new FileStorageService.StoredObject(stream, contentType, stat.size(), objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client().statObject(
                    StatObjectArgs.builder().bucket(bucketName()).object(objectKey).build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void copyObject(String sourceKey, String destKey) throws Exception {
        client().copyObject(
                CopyObjectArgs.builder()
                        .bucket(bucketName())
                        .object(destKey)
                        .source(CopySource.builder().bucket(bucketName()).object(sourceKey).build())
                        .build()
        );
    }

    @Override
    public String uploadPartObject(String partObjectKey, InputStream data, long partSize) throws Exception {
        ObjectWriteResponse resp = client().putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName())
                        .object(partObjectKey)
                        .stream(data, partSize, -1)
                        .contentType("application/octet-stream")
                        .build()
        );
        return stripQuotes(resp != null ? resp.etag() : null);
    }

    @Override
    public void composeParts(String destObjectKey, List<String> partObjectKeys, String contentType) throws Exception {
        List<ComposeSource> sources = new ArrayList<>(partObjectKeys.size());
        for (String partKey : partObjectKeys) {
            sources.add(ComposeSource.builder().bucket(bucketName()).object(partKey).build());
        }
        client().composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketName())
                        .object(destObjectKey)
                        .sources(sources)
                        .headers(java.util.Map.of("Content-Type", contentType))
                        .build()
        );
    }

    @Override
    public void deletePartObject(String partObjectKey) {
        try {
            client().removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName()).object(partObjectKey).build()
            );
        } catch (Exception e) {
            log.warn("清理 MinIO 临时分片失败: key={}, err={}", partObjectKey, e.getMessage());
        }
    }

    @Override
    public long statPartSize(String partObjectKey) throws Exception {
        StatObjectResponse stat = client().statObject(
                StatObjectArgs.builder().bucket(bucketName()).object(partObjectKey).build()
        );
        return stat.size();
    }

    @Override
    public String extractObjectKey(String objectKeyOrUrl) {
        if (!StringUtils.hasText(objectKeyOrUrl)) {
            return objectKeyOrUrl;
        }
        String raw = objectKeyOrUrl.trim();
        int q = raw.indexOf('?');
        if (q >= 0) {
            raw = raw.substring(0, q);
        }
        for (String prefix : urlPrefixes()) {
            if (raw.startsWith(prefix)) {
                return raw.substring(prefix.length());
            }
        }
        return raw;
    }

    @Override
    public List<String> urlPrefixes() {
        LinkxProperties.Minio minio = linkxProperties.getMinio();
        String bucket = minio.getBucketName();
        String endpoint = minio.getEndpoint();
        List<String> prefixes = new ArrayList<>();
        if (StringUtils.hasText(endpoint) && StringUtils.hasText(bucket)) {
            prefixes.add(endpoint + "/" + bucket + "/");
            prefixes.add(endpoint.replace("http://", "https://") + "/" + bucket + "/");
        }
        prefixes.add("http://localhost:9000/" + bucket + "/");
        prefixes.add("http://127.0.0.1:9000/" + bucket + "/");
        prefixes.add("https://localhost:9000/" + bucket + "/");
        prefixes.add("https://127.0.0.1:9000/" + bucket + "/");
        return prefixes;
    }

    @Override
    public String beginNativeMultipartUpload(String objectKey, String contentType) throws Exception {
        return asyncClient().createMultipartUpload(bucketName(), objectKey, contentType);
    }

    @Override
    public String presignNativeUploadPart(String objectKey, String uploadId, int partNumber) throws Exception {
        return S3NativeMultipartSupport.presignUploadPart(
                client(),
                bucketName(),
                objectKey,
                uploadId,
                partNumber,
                S3NativeMultipartSupport.PRESIGN_PART_EXPIRY_SECONDS);
    }

    @Override
    public void completeNativeMultipartUpload(
            String objectKey, String uploadId, List<S3NativeMultipartSupport.UploadedPart> parts) throws Exception {
        asyncClient().completeMultipartUpload(bucketName(), objectKey, uploadId, parts);
    }

    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = buildClient();
                    asyncClient = buildAsyncClient();
                }
            }
        }
        return client;
    }

    /** 配置热更新后重建客户端 */
    public void reloadClient() {
        synchronized (this) {
            client = buildClient();
            asyncClient = buildAsyncClient();
        }
    }

    private MinioClient buildClient() {
        LinkxProperties.Minio minio = linkxProperties.getMinio();
        if (!StringUtils.hasText(minio.getAccessKey()) || !StringUtils.hasText(minio.getSecretKey())) {
            throw new IllegalStateException("MinIO 凭证未配置");
        }
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .httpClient(httpClient)
                .build();
    }

    private ExtendedMinioAsyncClient asyncClient() {
        if (asyncClient == null) {
            synchronized (this) {
                if (asyncClient == null) {
                    asyncClient = buildAsyncClient();
                }
            }
        }
        return asyncClient;
    }

    private ExtendedMinioAsyncClient buildAsyncClient() {
        LinkxProperties.Minio minio = linkxProperties.getMinio();
        if (!StringUtils.hasText(minio.getAccessKey()) || !StringUtils.hasText(minio.getSecretKey())) {
            throw new IllegalStateException("MinIO 凭证未配置");
        }
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build();
        return ExtendedMinioAsyncClient.wrap(
                MinioAsyncClient.builder()
                        .endpoint(minio.getEndpoint())
                        .credentials(minio.getAccessKey(), minio.getSecretKey())
                        .httpClient(httpClient)
                        .build());
    }

    private String bucketName() {
        return linkxProperties.getMinio().getBucketName();
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return "";
        }
        String s = etag.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
