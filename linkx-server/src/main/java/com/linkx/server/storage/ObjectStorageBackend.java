package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.FileStorageService;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储后端抽象（MinIO / OSS / 本地磁盘）。
 */
public interface ObjectStorageBackend {

    StorageProviderType providerType();

    /** 连通性探测（读写权限、桶可达等） */
    void testConnection() throws Exception;

    void putObject(String objectKey, InputStream stream, long size, String contentType) throws Exception;

    void deleteObject(String objectKey) throws Exception;

    String presignGet(String objectKey, int expirySeconds);

    FileStorageService.StoredObject open(String objectKey) throws Exception;

    boolean exists(String objectKey);

    void copyObject(String sourceKey, String destKey) throws Exception;

    String uploadPartObject(String partObjectKey, InputStream data, long partSize) throws Exception;

    void composeParts(String destObjectKey, List<String> partObjectKeys, String contentType) throws Exception;

    void deletePartObject(String partObjectKey);

    long statPartSize(String partObjectKey) throws Exception;

    /** 从 URL 或 key 抽出规范 object key */
    String extractObjectKey(String objectKeyOrUrl);

    /** 本后端可识别的 URL 前缀（用于解析历史完整 URL） */
    List<String> urlPrefixes();
}
