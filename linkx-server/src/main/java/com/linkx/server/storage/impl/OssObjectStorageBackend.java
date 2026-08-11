package com.linkx.server.storage.impl;


/**
 * 作者：yangleduo
 */
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.storage.ObjectStorageBackend;
import com.linkx.server.storage.StorageProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class OssObjectStorageBackend implements ObjectStorageBackend {

    private final LinkxProperties linkxProperties;
    private volatile OSS client;

    public OssObjectStorageBackend(LinkxProperties linkxProperties) {
        this.linkxProperties = linkxProperties;
    }

    @Override
    public StorageProviderType providerType() {
        return StorageProviderType.OSS;
    }

    @Override
    public void testConnection() throws Exception {
        OSS oss = client();
        String bucket = bucketName();
        if (!oss.doesBucketExist(bucket)) {
            throw new IllegalStateException("OSS 桶不存在: " + bucket);
        }
        oss.getBucketInfo(bucket);
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long size, String contentType) throws Exception {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(size);
        if (StringUtils.hasText(contentType)) {
            meta.setContentType(contentType);
        }
        client().putObject(bucketName(), objectKey, stream, meta);
    }

    @Override
    public void deleteObject(String objectKey) throws Exception {
        client().deleteObject(bucketName(), objectKey);
    }

    @Override
    public String presignGet(String objectKey, int expirySeconds) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirySeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName(), objectKey);
            request.setExpiration(expiration);
            request.setMethod(com.aliyun.oss.HttpMethod.GET);
            URL url = client().generatePresignedUrl(request);
            return url != null ? url.toString() : null;
        } catch (Exception e) {
            log.error("OSS 预签名失败: key={}, err={}", objectKey, e.getMessage());
            return null;
        }
    }

    @Override
    public FileStorageService.StoredObject open(String objectKey) throws Exception {
        ObjectMetadata meta = client().getObjectMetadata(bucketName(), objectKey);
        InputStream stream = client().getObject(bucketName(), objectKey).getObjectContent();
        String contentType = meta.getContentType();
        if (!StringUtils.hasText(contentType)) {
            contentType = "application/octet-stream";
        }
        return new FileStorageService.StoredObject(stream, contentType, meta.getContentLength(), objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client().getObjectMetadata(bucketName(), objectKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void copyObject(String sourceKey, String destKey) throws Exception {
        client().copyObject(new CopyObjectRequest(bucketName(), sourceKey, bucketName(), destKey));
    }

    @Override
    public String uploadPartObject(String partObjectKey, InputStream data, long partSize) throws Exception {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(partSize);
        meta.setContentType("application/octet-stream");
        client().putObject(bucketName(), partObjectKey, data, meta);
        ObjectMetadata stored = client().getObjectMetadata(bucketName(), partObjectKey);
        return stored.getETag() != null ? stored.getETag() : "";
    }

    @Override
    public void composeParts(String destObjectKey, List<String> partObjectKeys, String contentType) throws Exception {
        // OSS 无 ComposeObject：顺序读分片写入目标对象
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        for (String partKey : partObjectKeys) {
            try (InputStream in = client().getObject(bucketName(), partKey).getObjectContent()) {
                in.transferTo(buffer);
            }
        }
        byte[] bytes = buffer.toByteArray();
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(bytes.length);
        meta.setContentType(contentType);
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes)) {
            client().putObject(bucketName(), destObjectKey, in, meta);
        }
    }

    @Override
    public void deletePartObject(String partObjectKey) {
        try {
            client().deleteObject(bucketName(), partObjectKey);
        } catch (Exception e) {
            log.warn("清理 OSS 临时分片失败: key={}, err={}", partObjectKey, e.getMessage());
        }
    }

    @Override
    public long statPartSize(String partObjectKey) throws Exception {
        return client().getObjectMetadata(bucketName(), partObjectKey).getContentLength();
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
        LinkxProperties.Oss oss = linkxProperties.getOss();
        String bucket = oss.getBucketName();
        List<String> prefixes = new ArrayList<>();
        if (!StringUtils.hasText(bucket)) {
            return prefixes;
        }
        String endpoint = normalizeEndpoint(oss.getEndpoint());
        if (StringUtils.hasText(endpoint)) {
            prefixes.add("https://" + endpoint + "/" + bucket + "/");
            prefixes.add("http://" + endpoint + "/" + bucket + "/");
            prefixes.add("https://" + bucket + "." + endpoint + "/");
            prefixes.add("http://" + bucket + "." + endpoint + "/");
        }
        if (StringUtils.hasText(oss.getCnameDomain())) {
            String cname = oss.getCnameDomain().trim();
            prefixes.add("https://" + cname + "/" + bucket + "/");
            prefixes.add("http://" + cname + "/" + bucket + "/");
            prefixes.add("https://" + bucket + "." + cname + "/");
            prefixes.add("http://" + bucket + "." + cname + "/");
        }
        return prefixes;
    }

    public void reloadClient() {
        synchronized (this) {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception ignored) {
                    // ignore
                }
            }
            client = buildClient();
        }
    }

    private OSS client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = buildClient();
                }
            }
        }
        return client;
    }

    private OSS buildClient() {
        LinkxProperties.Oss oss = linkxProperties.getOss();
        if (!StringUtils.hasText(oss.getAccessKeyId()) || !StringUtils.hasText(oss.getAccessKeySecret())) {
            throw new IllegalStateException("OSS 凭证未配置");
        }
        String endpoint = normalizeEndpoint(oss.getEndpoint());
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("OSS Endpoint 未配置");
        }
        return new OSSClientBuilder().build(
                "https://" + endpoint,
                oss.getAccessKeyId().trim(),
                oss.getAccessKeySecret().trim()
        );
    }

    private String bucketName() {
        return linkxProperties.getOss().getBucketName();
    }

    private static String normalizeEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        String e = endpoint.trim();
        if (e.startsWith("https://")) {
            e = e.substring(8);
        } else if (e.startsWith("http://")) {
            e = e.substring(7);
        }
        while (e.endsWith("/")) {
            e = e.substring(0, e.length() - 1);
        }
        return e;
    }
}
