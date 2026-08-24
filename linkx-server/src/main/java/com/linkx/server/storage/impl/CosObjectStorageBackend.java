package com.linkx.server.storage.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.storage.ObjectStorageBackend;
import com.linkx.server.storage.StorageProviderType;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.CopyObjectRequest;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
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
public class CosObjectStorageBackend implements ObjectStorageBackend {

    private final LinkxProperties linkxProperties;
    private volatile COSClient client;

    public CosObjectStorageBackend(LinkxProperties linkxProperties) {
        this.linkxProperties = linkxProperties;
    }

    @Override
    public StorageProviderType providerType() {
        return StorageProviderType.COS;
    }

    @Override
    public void testConnection() throws Exception {
        COSClient cos = client();
        String bucket = bucketName();
        if (!cos.doesBucketExist(bucket)) {
            throw new IllegalStateException("COS 桶不存在: " + bucket);
        }
        cos.getBucketLocation(bucket);
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long size, String contentType) throws Exception {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(size);
        if (StringUtils.hasText(contentType)) {
            meta.setContentType(contentType);
        }
        PutObjectRequest request = new PutObjectRequest(bucketName(), objectKey, stream, meta);
        client().putObject(request);
    }

    @Override
    public void deleteObject(String objectKey) throws Exception {
        client().deleteObject(bucketName(), objectKey);
    }

    @Override
    public String presignGet(String objectKey, int expirySeconds) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirySeconds * 1000L);
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucketName(), objectKey, HttpMethodName.GET);
            request.setExpiration(expiration);
            URL url = client().generatePresignedUrl(request);
            return url != null ? url.toString() : null;
        } catch (Exception e) {
            log.error("COS 预签名失败: key={}, err={}", objectKey, e.getMessage());
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
        CopyObjectRequest request = new CopyObjectRequest(bucketName(), sourceKey, bucketName(), destKey);
        client().copyObject(request);
    }

    @Override
    public String uploadPartObject(String partObjectKey, InputStream data, long partSize) throws Exception {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(partSize);
        meta.setContentType("application/octet-stream");
        PutObjectRequest request = new PutObjectRequest(bucketName(), partObjectKey, data, meta);
        PutObjectResult result = client().putObject(request);
        return result.getETag() != null ? result.getETag() : "";
    }

    @Override
    public void composeParts(String destObjectKey, List<String> partObjectKeys, String contentType) throws Exception {
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
            PutObjectRequest request = new PutObjectRequest(bucketName(), destObjectKey, in, meta);
            client().putObject(request);
        }
    }

    @Override
    public void deletePartObject(String partObjectKey) {
        try {
            client().deleteObject(bucketName(), partObjectKey);
        } catch (Exception e) {
            log.warn("清理 COS 临时分片失败: key={}, err={}", partObjectKey, e.getMessage());
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
        LinkxProperties.Cos cos = linkxProperties.getCos();
        String bucket = cos.getBucketName();
        List<String> prefixes = new ArrayList<>();
        if (!StringUtils.hasText(bucket)) {
            return prefixes;
        }
        String region = normalizeRegion(cos.getRegion());
        if (StringUtils.hasText(region)) {
            prefixes.add("https://" + bucket + ".cos." + region + ".myqcloud.com/");
            prefixes.add("http://" + bucket + ".cos." + region + ".myqcloud.com/");
            prefixes.add("https://cos." + region + ".myqcloud.com/" + bucket + "/");
            prefixes.add("http://cos." + region + ".myqcloud.com/" + bucket + "/");
        }
        if (StringUtils.hasText(cos.getCnameDomain())) {
            String cname = cos.getCnameDomain().trim();
            prefixes.add("https://" + cname + "/");
            prefixes.add("http://" + cname + "/");
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

    private COSClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = buildClient();
                }
            }
        }
        return client;
    }

    private COSClient buildClient() {
        LinkxProperties.Cos cos = linkxProperties.getCos();
        if (!StringUtils.hasText(cos.getSecretId()) || !StringUtils.hasText(cos.getSecretKey())) {
            throw new IllegalStateException("COS 凭证未配置");
        }
        String region = normalizeRegion(cos.getRegion());
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("COS Region 未配置");
        }
        COSCredentials cred = new BasicCOSCredentials(cos.getSecretId().trim(), cos.getSecretKey().trim());
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        return new COSClient(cred, clientConfig);
    }

    private String bucketName() {
        return linkxProperties.getCos().getBucketName();
    }

    private static String normalizeRegion(String region) {
        if (!StringUtils.hasText(region)) {
            return "";
        }
        return region.trim();
    }
}
