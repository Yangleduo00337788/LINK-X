package com.linkx.server.storage.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.StoredMediaProxyService;
import com.linkx.server.storage.ObjectStorageBackend;
import com.linkx.server.storage.StorageProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class LocalObjectStorageBackend implements ObjectStorageBackend {

    private final LinkxProperties linkxProperties;
    private final StoredMediaProxyService storedMediaProxyService;

    public LocalObjectStorageBackend(
            LinkxProperties linkxProperties,
            @Lazy StoredMediaProxyService storedMediaProxyService) {
        this.linkxProperties = linkxProperties;
        this.storedMediaProxyService = storedMediaProxyService;
    }

    @Override
    public StorageProviderType providerType() {
        return StorageProviderType.LOCAL;
    }

    @Override
    public void testConnection() throws Exception {
        Path root = resolveRoot();
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("本地存储目录不可写: " + root);
        }
        Path probe = root.resolve(".linkx-probe");
        Files.writeString(probe, "ok");
        Files.deleteIfExists(probe);
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long size, String contentType) throws Exception {
        Path target = resolveObjectPath(objectKey);
        Files.createDirectories(target.getParent());
        try (InputStream in = stream; FileOutputStream out = new FileOutputStream(target.toFile())) {
            in.transferTo(out);
        }
    }

    @Override
    public void deleteObject(String objectKey) throws Exception {
        Files.deleteIfExists(resolveObjectPath(objectKey));
    }

    @Override
    public String presignGet(String objectKey, int expirySeconds) {
        return storedMediaProxyService.wrapObjectKey(objectKey, expirySeconds);
    }

    @Override
    public FileStorageService.StoredObject open(String objectKey) throws Exception {
        Path path = resolveObjectPath(objectKey);
        if (!Files.exists(path)) {
            throw new java.io.FileNotFoundException(objectKey);
        }
        long size = Files.size(path);
        String contentType = Files.probeContentType(path);
        if (!StringUtils.hasText(contentType)) {
            contentType = "application/octet-stream";
        }
        return new FileStorageService.StoredObject(new FileInputStream(path.toFile()), contentType, size, objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolveObjectPath(objectKey));
    }

    @Override
    public void copyObject(String sourceKey, String destKey) throws Exception {
        Path source = resolveObjectPath(sourceKey);
        Path dest = resolveObjectPath(destKey);
        Files.createDirectories(dest.getParent());
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public String uploadPartObject(String partObjectKey, InputStream data, long partSize) throws Exception {
        Path target = resolveObjectPath(partObjectKey);
        Files.createDirectories(target.getParent());
        try (InputStream in = data; FileOutputStream out = new FileOutputStream(target.toFile())) {
            in.transferTo(out);
        }
        return "local-" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void composeParts(String destObjectKey, List<String> partObjectKeys, String contentType) throws Exception {
        Path dest = resolveObjectPath(destObjectKey);
        Files.createDirectories(dest.getParent());
        try (FileOutputStream out = new FileOutputStream(dest.toFile())) {
            for (String partKey : partObjectKeys) {
                Path part = resolveObjectPath(partKey);
                try (InputStream in = new FileInputStream(part.toFile())) {
                    in.transferTo(out);
                }
            }
        }
    }

    @Override
    public void deletePartObject(String partObjectKey) {
        try {
            Files.deleteIfExists(resolveObjectPath(partObjectKey));
        } catch (Exception e) {
            log.warn("清理本地临时分片失败: key={}, err={}", partObjectKey, e.getMessage());
        }
    }

    @Override
    public long statPartSize(String partObjectKey) throws Exception {
        return Files.size(resolveObjectPath(partObjectKey));
    }

    @Override
    public String extractObjectKey(String objectKeyOrUrl) {
        if (!StringUtils.hasText(objectKeyOrUrl)) {
            return objectKeyOrUrl;
        }
        String raw = objectKeyOrUrl.trim();
        if (raw.contains("/media/stored?")) {
            return raw;
        }
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
        return new ArrayList<>();
    }

    private Path resolveRoot() {
        String base = linkxProperties.getLocal().getBasePath();
        if (!StringUtils.hasText(base)) {
            base = "./data/local-storage";
        }
        return Path.of(base.trim()).toAbsolutePath().normalize();
    }

    private Path resolveObjectPath(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.contains("..") || objectKey.startsWith("/")
                || objectKey.contains("\\") || objectKey.contains("://")) {
            throw new IllegalArgumentException("非法对象 key");
        }
        Path root = resolveRoot();
        Path resolved = root.resolve(objectKey.replace('\\', '/')).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法对象 key");
        }
        return resolved;
    }
}
