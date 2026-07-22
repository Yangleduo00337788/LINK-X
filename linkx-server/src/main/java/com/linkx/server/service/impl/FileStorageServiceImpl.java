package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    /** 允许的 Content-Type 白名单 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            // 图片（image/jpg 为部分浏览器非标准别名）
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            // 文档
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/markdown",
            // 压缩包
            "application/zip", "application/x-7z-compressed", "application/x-rar-compressed",
            // 通用二进制（部分浏览器对未知类型上报）
            "application/octet-stream",
            // 音频（语音消息）
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm", "audio/aac", "audio/x-m4a"
    );

    /** 允许的扩展名白名单（与 Content-Type 共同校验） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".md",
            ".zip", ".7z", ".rar",
            ".mp3", ".wav", ".ogg", ".m4a"
    );

    /** 预签名 URL 默认有效期：1 小时 */
    private static final int DEFAULT_PRESIGN_EXPIRY_SECONDS = 3600;

    private final MinioClient minioClient;
    private final LinkxProperties linkxProperties;

    @Override
    public String uploadFile(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 1. 文件大小校验
        long maxSize = linkxProperties.getMinio().getMaxFileSize();
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("文件大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
        }

        // 2. 解析并清洗原始文件名（防路径穿越 ../）
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            originalFilename = sanitizeFilename(originalFilename);
        }
        String extension = extractExtension(originalFilename);

        // 3. MIME 与扩展名白名单校验
        String contentType = file.getContentType();
        if (contentType != null && "image/jpg".equalsIgnoreCase(contentType)) {
            contentType = "image/jpeg";
        }
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不允许的文件类型: " + contentType);
        }
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不允许的文件扩展名: " + extension);
        }

        // 4. 生成 UUID 文件名（避免使用用户提供的文件名）
        String objectBaseName = (fileName != null && !fileName.isEmpty())
                ? sanitizeFilename(fileName)
                : UUID.randomUUID().toString().replace("-", "");
        String objectName = objectBaseName + extension;

        // 5. 按日期组织路径
        String pathPrefix = LocalDate.now().toString().replace("-", "/") + "/";
        String fullObjectName = pathPrefix + objectName;

        try {
            String bucketName = linkxProperties.getMinio().getBucketName();
            InputStream is = file.getInputStream();
            // 显式声明 contentType，bucket 设为私有后由签名 URL 提供访问
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fullObjectName)
                            .stream(is, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );

            // 返回对象 key（不返回公开 URL），供 getPresignedUrl 生成带签名的临时链接
            return fullObjectName;

        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("文件上传失败", e);
            // 不向调用方暴露内部异常详情
            throw new RuntimeException("文件上传失败");
        }
    }

    @Override
    public void deleteFile(String objectKeyOrUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isEmpty()) {
            return;
        }
        // 兼容旧数据：以前存的是公开 URL
        String objectName = extractObjectName(objectKeyOrUrl);
        if (objectName == null || objectName.isEmpty()) {
            log.warn("Unable to resolve object name from: {}", objectKeyOrUrl);
            return;
        }
        // 防御：禁止路径穿越
        if (objectName.contains("..") || objectName.startsWith("/")) {
            log.warn("Invalid object name: {}", objectName);
            return;
        }

        try {
            String bucketName = linkxProperties.getMinio().getBucketName();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", objectName);

        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("删除文件失败", e);
            throw new RuntimeException("删除文件失败");
        }
    }

    /**
     * 兼容：传入完整 URL 或纯粹的对象 key，都提取出对象名
     */
    private String extractObjectName(String urlOrKey) {
        String raw = urlOrKey;
        int q = raw.indexOf('?');
        if (q >= 0) {
            raw = raw.substring(0, q);
        }
        String bucketName = linkxProperties.getMinio().getBucketName();
        String endpoint = linkxProperties.getMinio().getEndpoint();
        String[] prefixes = {
                endpoint + "/" + bucketName + "/",
                "http://localhost:9000/" + bucketName + "/",
                "http://127.0.0.1:9000/" + bucketName + "/",
                "https://localhost:9000/" + bucketName + "/",
                "https://127.0.0.1:9000/" + bucketName + "/"
        };
        for (String prefix : prefixes) {
            if (raw.startsWith(prefix)) {
                return raw.substring(prefix.length());
            }
        }
        // 否则当作对象 key 直接使用
        return raw;
    }

    /**
     * 生成对象预签名 URL（私有 bucket 默认情况下前端无法直接访问，
     * 此方法生成带签名的临时 URL，expire 秒后失效）
     *
     * @param objectName 对象名（uploadFile 返回值）
     * @param expiry     过期秒数；≤0 用默认值 3600
     */
    @Override
    public String getPresignedUrl(String objectName, int expiry) {
        if (objectName == null || objectName.isEmpty()) {
            return null;
        }
        // 兼容传入完整 URL / 带 query 的旧链接，统一抽出 object key 再签名
        String key = extractObjectName(objectName);
        if (key.startsWith("/") || key.startsWith("data:") || key.startsWith("blob:")) {
            return key;
        }
        int seconds = expiry > 0 ? expiry : DEFAULT_PRESIGN_EXPIRY_SECONDS;
        try {
            String bucketName = linkxProperties.getMinio().getBucketName();
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(key)
                            .expiry(seconds)
                            .build()
            );
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("生成签名 URL 失败: key={}, err={}", key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 提供默认过期时间的预签名 URL 重载
     */
    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(objectName, DEFAULT_PRESIGN_EXPIRY_SECONDS);
    }

    /**
     * 清洗文件名，去掉路径分隔符，只保留最后一段
     */
    private String sanitizeFilename(String name) {
        String cleaned = name.replace('\\', '/');
        int idx = cleaned.lastIndexOf('/');
        if (idx >= 0) cleaned = cleaned.substring(idx + 1);
        return cleaned;
    }

    /**
     * 安全提取扩展名（带点号），不存在则返回空字符串
     */
    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
