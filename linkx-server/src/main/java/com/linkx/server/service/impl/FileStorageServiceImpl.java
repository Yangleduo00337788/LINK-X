package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final LinkxProperties linkxProperties;

    @Override
    public String uploadFile(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 检查文件大小
        long maxSize = linkxProperties.getMinio().getMaxFileSize();
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("文件大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 生成唯一文件名
        String objectName = fileName != null && !fileName.isEmpty()
                ? fileName
                : UUID.randomUUID().toString().replace("-", "") + extension;

        // 按日期组织文件路径
        String pathPrefix = java.time.LocalDate.now().toString().replace("-", "/") + "/";
        String fullObjectName = pathPrefix + objectName;

        try {
            String bucketName = linkxProperties.getMinio().getBucketName();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fullObjectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 返回可访问的 URL
            String endpoint = linkxProperties.getMinio().getEndpoint();
            return endpoint + "/" + bucketName + "/" + fullObjectName;

        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        try {
            String bucketName = linkxProperties.getMinio().getBucketName();
            String endpoint = linkxProperties.getMinio().getEndpoint();

            // 从 URL 中提取对象名
            String prefix = endpoint + "/" + bucketName + "/";
            if (!fileUrl.startsWith(prefix)) {
                log.warn("Invalid file URL: {}", fileUrl);
                return;
            }

            String objectName = fileUrl.substring(prefix.length());

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", objectName);

        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("删除文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("删除文件失败: " + e.getMessage());
        }
    }

    @Override
    public String getPresignedUrl(String objectName, int expiry) {
        // 简化版直接返回公开 URL，如需预签名可扩展
        String endpoint = linkxProperties.getMinio().getEndpoint();
        String bucketName = linkxProperties.getMinio().getBucketName();
        return endpoint + "/" + bucketName + "/" + objectName;
    }
}
