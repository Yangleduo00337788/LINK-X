package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {

    /**
     * 上传文件（私有桶，返回对象 key，由 getPresignedUrl 生成访问链接）
     *
     * @param file     文件
     * @param fileName 自定义文件名（可选）
     * @return 对象 key（如 "2026/07/15/uuid.jpg"）
     */
    String uploadFile(MultipartFile file, String fileName);

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 对象 key
     */
    default String uploadFile(MultipartFile file) {
        return uploadFile(file, null);
    }

    /**
     * 上传客户端安装包（管理端版本发布专用，路径前缀 releases/）。
     */
    InstallerUploadResult uploadInstaller(MultipartFile file);

    record InstallerUploadResult(String objectKey, String sha256, String fileName, long size) {
    }

    /**
     * 删除对象
     *
     * @param objectName 对象 key（uploadFile 返回值）
     */
    void deleteFile(String objectName);

    /**
     * 异步删除对象（用于批量清理等非关键路径，失败仅打日志）
     *
     * @param objectKey 对象 key
     */
    default void deleteFileAsync(String objectKey) {
        // 默认实现为空，子类可覆盖
    }

    /**
     * 从 object key / 完整 MinIO URL（可含预签名 query）抽出规范 object key。
     * 非本桶 URL 时原样返回（调用方应先排除外链）。
     */
    String extractObjectKey(String objectKeyOrUrl);

    /**
     * 获取文件预签名 URL（用于临时访问私有 bucket 中的文件）
     *
     * @param objectName 对象名
     * @param expiry     过期秒数
     * @return 预签名 URL
     */
    String getPresignedUrl(String objectName, int expiry);

    /**
     * 默认过期时间（1 小时）的预签名 URL 重载
     */
    default String getPresignedUrl(String objectName) {
        return getPresignedUrl(objectName, 3600);
    }

    /**
     * 打开对象输入流（供鉴权后的后端中转下载，调用方负责关闭流）
     */
    StoredObject openObject(String objectKeyOrUrl);

    /**
     * MinIO 对象只读视图
     */
    record StoredObject(InputStream stream, String contentType, long size, String objectKey) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            if (stream != null) {
                stream.close();
            }
        }
    }

    /** 分片上传的 partNumber + etag */
    record PartETag(int partNumber, String etag) {
    }

    /** 分片会话（服务端生成 objectName） */
    record MultipartSession(String uploadId, String objectName, String contentType) {
    }

    /** 按日期路径分配对象 key（不含实际上传） */
    String allocateObjectName(String originalFilename);

    /** 管理端安装包分片上传会话 */
    record InstallerMultipartSession(String uploadId, String objectKey) {
    }

    /** 初始化安装包分片上传（releases/ 路径） */
    InstallerMultipartSession initiateInstallerMultipart(String originalFileName);

    /** 完成安装包分片上传；packageSha256 可选，提供则跳过从 OSS 回拉整包算哈希 */
    InstallerUploadResult completeInstallerMultipart(
            String objectKey, String uploadId, String fileName, long fileSize, String packageSha256);

    /** 初始化分片上传，返回 uploadId + objectName */
    MultipartSession initiateMultipartUpload(String objectName, String contentType);

    /** 上传单个分片，返回 etag；已上传过的 part 幂等返回原 etag */
    String uploadPart(String objectName, String uploadId, int partNumber, InputStream data, long partSize);

    /** 列出已上传分片（断点续传） */
    List<PartETag> listUploadedParts(String uploadId);

    /** 完成分片上传，返回最终对象 key */
    String completeMultipartUpload(String objectName, String uploadId, List<PartETag> parts);

    /** 取消分片上传 */
    void abortMultipartUpload(String objectName, String uploadId);

    /** 对象是否仍存在于存储中 */
    boolean objectExists(String objectKey);

    /**
     * 同桶复制对象到新 key（用于转发：新对象由转发者 claim，避免复用他人 key 旁路属主校验）。
     *
     * @param sourceObjectKey  源 object key（或可抽出 key 的本桶 URL）
     * @param preferredFileName 用于生成新 key 扩展名的文件名；可空则沿用源 key 扩展名
     * @return 新 object key
     */
    String copyObject(String sourceObjectKey, String preferredFileName);

    /**
     * 检查内容哈希对应的对象是否存在于存储中（仅返回存在性，不返回 objectKey）。
     * 秒传逻辑应在 ChatService 层通过 ObjectKeyOwnershipService 校验属主后再返回文件。
     *
     * @param contentHash SHA-256 哈希值（64位十六进制）
     * @return true 如果对象存在且可访问
     */
    boolean existsByContentHash(String contentHash);

    /** 保存内容哈希与对象 key 映射 */
    void saveContentHash(String contentHash, String objectKey);

    /**
     * 根据内容哈希获取 objectKey（仅供内部使用，不对外暴露）。
     *
     * @param contentHash SHA-256 哈希值（64位十六进制）
     * @return objectKey，如果不存在或哈希无效则返回 null
     */
    String getObjectKeyByHashInternal(String contentHash);
}
