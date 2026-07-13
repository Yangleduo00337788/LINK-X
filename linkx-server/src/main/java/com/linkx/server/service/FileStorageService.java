package com.linkx.server.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file     文件
     * @param fileName 自定义文件名（可选）
     * @return 文件访问 URL
     */
    String uploadFile(MultipartFile file, String fileName);

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 文件访问 URL
     */
    default String uploadFile(MultipartFile file) {
        return uploadFile(file, null);
    }

    /**
     * 删除文件
     *
     * @param fileUrl 文件 URL
     */
    void deleteFile(String fileUrl);

    /**
     * 获取文件预签名 URL（用于临时访问私有 bucket 中的文件）
     *
     * @param objectName 对象名
     * @param expiry     过期时间（秒）
     * @return 预签名 URL
     */
    String getPresignedUrl(String objectName, int expiry);
}
