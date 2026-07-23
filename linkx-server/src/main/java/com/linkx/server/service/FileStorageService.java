package com.linkx.server.service;

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
     * 删除对象
     *
     * @param objectName 对象 key（uploadFile 返回值）
     */
    void deleteFile(String objectName);

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

    /** 初始化分片上传，返回 uploadId */
    String initiateMultipartUpload(String objectName, String contentType);

    /** 上传单个分片 */
    void uploadPart(String objectName, String uploadId, int partNumber, InputStream data, long partSize);

    /** 完成分片上传，返回最终对象 key */
    String completeMultipartUpload(String objectName, String uploadId, List<PartETag> parts);

    /** 取消分片上传 */
    void abortMultipartUpload(String objectName, String uploadId);

    /** 按内容哈希查找已上传对象（秒传） */
    String findByContentHash(String contentHash);

    /** 保存内容哈希与对象 key 映射 */
    void saveContentHash(String contentHash, String objectKey);
}
