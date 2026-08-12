package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.FileExtensionValidator;
import com.linkx.server.common.InstallerUploadValidator;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.storage.ObjectStorageBackend;
import com.linkx.server.storage.ObjectStorageRouter;
import com.linkx.server.storage.StorageProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm", "audio/aac", "audio/x-m4a",
            // 视频（友链）
            "video/mp4", "video/webm", "video/quicktime"
    );

    /** 允许的扩展名白名单（与 Content-Type 共同校验） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".md",
            ".zip", ".7z", ".rar",
            ".mp3", ".wav", ".ogg", ".m4a", ".webm",
            ".mp4", ".mov"
    );

    /** 预签名 URL 默认有效期：1 小时 */
    private static final int DEFAULT_PRESIGN_EXPIRY_SECONDS = 3600;

    /** 分片会话 Redis TTL */
    private static final Duration MULTIPART_TTL = Duration.ofHours(24);

    /** 秒传哈希 TTL */
    private static final Duration FILE_HASH_TTL = Duration.ofDays(30);

    /** ComposeObject 除最后一片外，单片至少 5MiB（S3 约束） */
    private static final long MIN_COMPOSE_PART_BYTES = 5L * 1024 * 1024;

    private static final Set<String> INSTALLER_EXTENSIONS = Set.of(
            ".exe", ".msi", ".dmg", ".deb", ".rpm", ".appimage"
    );

    private static final String HASH_KEY_PREFIX = "linkx:filehash:";
    private static final String MP_META_PREFIX = "linkx:mp:meta:";
    private static final String MP_PARTS_PREFIX = "linkx:mp:parts:";

    private final ObjectStorageRouter objectStorageRouter;
    private final LinkxProperties linkxProperties;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("SpringJavaInjectionPointAutowiringInspection")
    @Qualifier("minioCleanupExecutor")
    private final java.util.concurrent.ExecutorService minioCleanupExecutor;

    @Override
    @Async("minioCleanupExecutor")
    public void deleteFileAsync(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            deleteFile(objectKey);
            log.debug("异步删除对象完成: {}", objectKey);
        } catch (Exception e) {
            log.warn("异步删除对象失败: key={}, err={}", objectKey, e.getMessage());
        }
    }

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

        // 3. MIME 与扩展名白名单校验（剥离 ;codecs=opus 等参数）
        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("不允许的文件类型: " + file.getContentType());
        }
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不允许的文件扩展名: " + extension);
        }

        // magic bytes 校验：Content-Type 可伪造，需校验文件头特征防止伪装文件
        if (!FileExtensionValidator.hasSafeContentSignature(file)) {
            throw new CustomException(400, "文件内容与扩展名不匹配");
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
            ObjectStorageBackend backend = objectStorageRouter.activeBackend();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(raw, digest)) {
                backend.putObject(fullObjectName, dis, file.getSize(), contentType);
            }

            String contentHash = HexFormat.of().formatHex(digest.digest());
            saveContentHash(contentHash, fullObjectName);

            // 返回对象 key（不返回公开 URL），供 getPresignedUrl 生成带签名的临时链接
            return fullObjectName;

        } catch (Exception e) {
            log.error("文件上传失败", e);
            // 不向调用方暴露内部异常详情
            throw new RuntimeException("文件上传失败");
        }
    }

    @Override
    public InstallerUploadResult uploadInstaller(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("安装包不能为空");
        }
        try {
            InstallerUploadValidator.assertInstallerFile(file);
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        }

        long maxSize = linkxProperties.getMinio().getMaxFileSize();
        if (file.getSize() > maxSize) {
            throw new CustomException(400, "安装包大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            originalFilename = sanitizeFilename(originalFilename);
        }
        String extension = extractExtension(originalFilename);
        String baseName = originalFilename != null && originalFilename.length() > extension.length()
                ? originalFilename.substring(0, originalFilename.length() - extension.length())
                : UUID.randomUUID().toString().replace("-", "");
        if (!StringUtils.hasText(baseName)) {
            baseName = UUID.randomUUID().toString().replace("-", "");
        }
        String objectName = "releases/"
                + LocalDate.now().toString().replace("-", "/")
                + "/"
                + baseName
                + extension;

        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        try {
            ObjectStorageBackend backend = objectStorageRouter.activeBackend();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(raw, digest)) {
                backend.putObject(objectName, dis, file.getSize(), contentType);
            }
            String contentHash = HexFormat.of().formatHex(digest.digest());
            saveContentHash(contentHash, objectName);
            String displayName = originalFilename != null ? originalFilename : objectName;
            return new InstallerUploadResult(objectName, contentHash, displayName, file.getSize());
        } catch (Exception e) {
            log.error("安装包上传失败", e);
            throw new RuntimeException("安装包上传失败");
        }
    }

    @Override
    public InstallerMultipartSession initiateInstallerMultipart(String originalFileName) {
        assertInstallerFileName(originalFileName);
        String objectName = buildInstallerObjectName(originalFileName);
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("objectName", objectName);
        meta.put("contentType", "application/octet-stream");
        meta.put("installer", "true");
        try {
            redisTemplate.opsForValue().set(MP_META_PREFIX + uploadId, objectMapper.writeValueAsString(meta), MULTIPART_TTL);
            redisTemplate.expire(MP_PARTS_PREFIX + uploadId, MULTIPART_TTL);
        } catch (Exception e) {
            log.error("初始化安装包分片会话失败: objectName={}", objectName, e);
            throw new RuntimeException("初始化安装包分片上传失败");
        }
        return new InstallerMultipartSession(uploadId, objectName);
    }

    @Override
    public InstallerUploadResult completeInstallerMultipart(
            String objectKey, String uploadId, String fileName, long fileSize, String packageSha256) {
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith("releases/")) {
            throw new CustomException(400, "非法安装包对象路径");
        }
        long maxSize = linkxProperties.getMinio().getMaxFileSize();
        if (fileSize > maxSize) {
            throw new CustomException(400, "安装包大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
        }
        List<PartETag> parts = listUploadedParts(uploadId);
        String mergedKey = completeMultipartUpload(objectKey, uploadId, parts);
        try {
            String contentHash = resolveInstallerSha256(mergedKey, packageSha256);
            saveContentHash(contentHash, mergedKey);
            String displayName = StringUtils.hasText(fileName) ? sanitizeFilename(fileName) : mergedKey;
            return new InstallerUploadResult(mergedKey, contentHash, displayName, fileSize);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("完成安装包分片上传失败: uploadId={}, objectKey={}", uploadId, objectKey, e);
            throw new RuntimeException("完成安装包分片上传失败");
        }
    }

    private String resolveInstallerSha256(String mergedKey, String packageSha256) throws Exception {
        if (StringUtils.hasText(packageSha256)) {
            String normalized = packageSha256.trim().toLowerCase(Locale.ROOT);
            if (normalized.matches("[a-f0-9]{64}")) {
                return normalized;
            }
            throw new CustomException(400, "packageSha256 格式无效");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (StoredObject stored = openObject(mergedKey);
             InputStream in = stored.stream()) {
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void assertInstallerFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            throw new CustomException(400, "安装包文件名不能为空");
        }
        String cleaned = sanitizeFilename(originalFileName.trim());
        String ext = extractExtension(cleaned).toLowerCase(Locale.ROOT);
        if (!INSTALLER_EXTENSIONS.contains(ext)) {
            throw new CustomException(400, "仅支持 .exe / .msi / .dmg / .deb / .rpm / .AppImage 安装包");
        }
    }

    private String buildInstallerObjectName(String originalFileName) {
        String cleaned = sanitizeFilename(originalFileName.trim());
        String extension = extractExtension(cleaned);
        String baseName = cleaned.length() > extension.length()
                ? cleaned.substring(0, cleaned.length() - extension.length())
                : UUID.randomUUID().toString().replace("-", "");
        if (!StringUtils.hasText(baseName)) {
            baseName = UUID.randomUUID().toString().replace("-", "");
        }
        return "releases/"
                + LocalDate.now().toString().replace("-", "/")
                + "/"
                + baseName
                + extension;
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
            objectStorageRouter.activeBackend().deleteObject(objectName);
            log.info("Deleted file from storage: {}", objectName);

        } catch (Exception e) {
            log.error("删除文件失败", e);
            throw new RuntimeException("删除文件失败");
        }
    }

    /**
     * 兼容：传入完整 URL 或纯粹的对象 key，都提取出对象名
     */
    @Override
    public String extractObjectKey(String objectKeyOrUrl) {
        return extractObjectName(objectKeyOrUrl);
    }

    private String extractObjectName(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) {
            return urlOrKey;
        }
        String raw = urlOrKey.trim();
        if (raw.contains("/media/stored?")) {
            return raw;
        }
        int q = raw.indexOf('?');
        if (q >= 0) {
            raw = raw.substring(0, q);
        }
        for (StorageProviderType type : StorageProviderType.values()) {
            for (String prefix : objectStorageRouter.backendFor(type).urlPrefixes()) {
                if (prefix != null && raw.startsWith(prefix)) {
                    return raw.substring(prefix.length());
                }
            }
        }
        return objectStorageRouter.activeBackend().extractObjectKey(raw);
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
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("/") || key.startsWith("data:") || key.startsWith("blob:")) {
            return key;
        }
        // 拒绝路径穿越与绝对/外链形态，避免误签任意对象
        if (key.contains("..") || key.contains("\\") || key.contains("://")) {
            log.warn("拒绝签发非法 object key: {}", key.length() > 80 ? key.substring(0, 80) + "…" : key);
            return null;
        }
        int seconds = expiry > 0 ? expiry : DEFAULT_PRESIGN_EXPIRY_SECONDS;
        return objectStorageRouter.activeBackend().presignGet(key, seconds);
    }

    /**
     * 提供默认过期时间的预签名 URL 重载
     */
    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(objectName, DEFAULT_PRESIGN_EXPIRY_SECONDS);
    }

    @Override
    public StoredObject openObject(String objectKeyOrUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isEmpty()) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        String key = extractObjectName(objectKeyOrUrl);
        if (key.startsWith("/") || key.startsWith("data:") || key.startsWith("blob:")) {
            throw new IllegalArgumentException("不支持的对象 key");
        }
        if (key.contains("..")) {
            throw new IllegalArgumentException("非法对象 key");
        }
        try {
            return objectStorageRouter.activeBackend().open(key);
        } catch (Exception e) {
            log.error("打开存储对象失败: key={}, err={}", key, e.getMessage());
            throw new RuntimeException("读取文件失败");
        }
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

    // ==================== 分片上传（临时对象 + composeObject，兼容 MinIO 8.x 公开 API） ====================

    @Override
    public String allocateObjectName(String originalFilename) {
        String cleaned = originalFilename != null ? sanitizeFilename(originalFilename) : null;
        String extension = extractExtension(cleaned);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不允许的文件扩展名: " + extension);
        }
        String objectBaseName = UUID.randomUUID().toString().replace("-", "");
        String pathPrefix = LocalDate.now().toString().replace("-", "/") + "/";
        return pathPrefix + objectBaseName + extension;
    }

    @Override
    public MultipartSession initiateMultipartUpload(String objectName, String contentType) {
        if (!StringUtils.hasText(objectName) || objectName.contains("..") || objectName.startsWith("/")) {
            throw new IllegalArgumentException("非法对象名");
        }
        String ct = normalizeContentType(contentType);
        if (ct == null || !ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw new IllegalArgumentException("不允许的文件类型: " + contentType);
        }
        String extension = extractExtension(objectName.contains("/")
                ? objectName.substring(objectName.lastIndexOf('/') + 1)
                : objectName);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不允许的文件扩展名: " + extension);
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("objectName", objectName);
        meta.put("contentType", ct);
        try {
            redisTemplate.opsForValue().set(MP_META_PREFIX + uploadId, objectMapper.writeValueAsString(meta), MULTIPART_TTL);
            redisTemplate.expire(MP_PARTS_PREFIX + uploadId, MULTIPART_TTL);
        } catch (Exception e) {
            log.error("初始化分片会话失败: objectName={}", objectName, e);
            throw new RuntimeException("初始化分片上传失败");
        }
        return new MultipartSession(uploadId, objectName, ct);
    }

    @Override
    public String uploadPart(String objectName, String uploadId, int partNumber, InputStream data, long partSize) {
        assertValidPartNumber(partNumber);
        MultipartMeta meta = requireMultipartMeta(uploadId);
        if (!meta.objectName().equals(objectName)) {
            throw new IllegalArgumentException("objectName 与分片会话不匹配");
        }

        String existing = redisTemplate.<String, String>opsForHash().get(MP_PARTS_PREFIX + uploadId, String.valueOf(partNumber));
        if (StringUtils.hasText(existing)) {
            return existing;
        }
        if (partSize <= 0) {
            throw new IllegalArgumentException("分片不能为空");
        }

        String partObjectKey = partObjectKey(uploadId, partNumber);
        try {
            ObjectStorageBackend backend = objectStorageRouter.activeBackend();
            String etag = backend.uploadPartObject(partObjectKey, data, partSize);
            if (!StringUtils.hasText(etag)) {
                etag = "fb-".concat(UUID.randomUUID().toString().replace("-", ""));
            }
            redisTemplate.opsForHash().put(MP_PARTS_PREFIX + uploadId, String.valueOf(partNumber), etag);
            redisTemplate.expire(MP_PARTS_PREFIX + uploadId, MULTIPART_TTL);
            redisTemplate.expire(MP_META_PREFIX + uploadId, MULTIPART_TTL);
            return etag;
        } catch (Exception e) {
            log.error("分片上传失败: uploadId={}, part={}", uploadId, partNumber, e);
            throw new RuntimeException("分片上传失败");
        }
    }

    @Override
    public List<PartETag> listUploadedParts(String uploadId) {
        requireMultipartMeta(uploadId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(MP_PARTS_PREFIX + uploadId);
        List<PartETag> parts = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            try {
                int pn = Integer.parseInt(String.valueOf(e.getKey()));
                parts.add(new PartETag(pn, String.valueOf(e.getValue())));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        parts.sort(Comparator.comparingInt(PartETag::partNumber));
        return parts;
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, List<PartETag> parts) {
        MultipartMeta meta = requireMultipartMeta(uploadId);
        if (!meta.objectName().equals(objectName)) {
            throw new IllegalArgumentException("objectName 与分片会话不匹配");
        }

        List<PartETag> ordered = resolvePartsForComplete(uploadId, parts);
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("没有可合并的分片");
        }
        // 校验连续 partNumber：1..N
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).partNumber() != i + 1) {
                throw new IllegalArgumentException("分片序号不连续，缺少 part " + (i + 1));
            }
        }
        if (ordered.size() > 32) {
            throw new IllegalArgumentException("分片数超过 ComposeObject 上限(32)，请增大分片大小");
        }

        ObjectStorageBackend backend = objectStorageRouter.activeBackend();
        try {
            // 校验中间片大小（Compose 约束）
            for (int i = 0; i < ordered.size() - 1; i++) {
                PartETag p = ordered.get(i);
                long partSize = backend.statPartSize(partObjectKey(uploadId, p.partNumber()));
                if (partSize < MIN_COMPOSE_PART_BYTES) {
                    throw new IllegalArgumentException("分片 " + p.partNumber() + " 小于 5MB，无法合并（除最后一片外）");
                }
            }

            List<String> partKeys = new ArrayList<>(ordered.size());
            for (PartETag p : ordered) {
                partKeys.add(partObjectKey(uploadId, p.partNumber()));
            }
            backend.composeParts(objectName, partKeys, meta.contentType());

            cleanupMultipartTemp(uploadId, ordered);
            return objectName;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("完成分片上传失败: uploadId={}, objectName={}", uploadId, objectName, e);
            throw new RuntimeException("完成分片上传失败");
        }
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            return;
        }
        // 先清 Redis 会话，再尽力清理 MinIO 临时对象，避免存储不可用时会话残留
        List<PartETag> parts = listUploadedPartsSafe(uploadId);
        redisTemplate.delete(MP_META_PREFIX + uploadId);
        redisTemplate.delete(MP_PARTS_PREFIX + uploadId);
        try {
            cleanupMultipartTempObjects(uploadId, parts);
        } catch (Exception e) {
            log.warn("取消分片上传清理临时对象失败: uploadId={}", uploadId, e);
        }
    }

    @Override
    public boolean objectExists(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }
        String key = extractObjectName(objectKey);
        if (key.contains("..") || key.startsWith("/")) {
            return false;
        }
        try {
            return objectStorageRouter.activeBackend().exists(key);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String copyObject(String sourceObjectKey, String preferredFileName) {
        if (!StringUtils.hasText(sourceObjectKey)) {
            throw new IllegalArgumentException("源对象 key 不能为空");
        }
        String sourceKey = extractObjectName(sourceObjectKey);
        if (!StringUtils.hasText(sourceKey)
                || sourceKey.contains("..")
                || sourceKey.startsWith("/")
                || sourceKey.contains("://")
                || sourceKey.contains("\\")) {
            throw new IllegalArgumentException("非法源对象 key");
        }
        if (!objectExists(sourceKey)) {
            throw new IllegalArgumentException("源附件已不存在");
        }

        String fileNameForAlloc = preferredFileName;
        if (!StringUtils.hasText(fileNameForAlloc) || extractExtension(sanitizeFilename(fileNameForAlloc)).isEmpty()) {
            String base = sourceKey.contains("/")
                    ? sourceKey.substring(sourceKey.lastIndexOf('/') + 1)
                    : sourceKey;
            if (!StringUtils.hasText(base) || extractExtension(base).isEmpty()) {
                throw new IllegalArgumentException("无法从源附件推断文件类型");
            }
            fileNameForAlloc = base;
        } else {
            fileNameForAlloc = sanitizeFilename(fileNameForAlloc);
        }

        String destKey = allocateObjectName(fileNameForAlloc);
        try {
            objectStorageRouter.activeBackend().copyObject(sourceKey, destKey);
            return destKey;
        } catch (Exception e) {
            log.error("复制对象失败: src={}, dest={}, err={}", sourceKey, destKey, e.getMessage());
            throw new RuntimeException("复制附件失败");
        }
    }

    // ==================== 文件秒传/去重 ====================

    /**
     * 公开 API：检查内容哈希对应的对象是否存在。
     * 业务层应通过 ObjectKeyOwnershipService 校验属主后再返回文件。
     */
    @Override
    public boolean existsByContentHash(String contentHash) {
        if (!StringUtils.hasText(contentHash) || !contentHash.matches("(?i)^[a-f0-9]{64}$")) {
            return false;
        }
        return getObjectKeyByHashInternal(contentHash) != null;
    }

    /**
     * 内部方法：根据内容哈希获取 objectKey（仅供属主校验，不对外暴露）。
     */
    @Override
    public String getObjectKeyByHashInternal(String contentHash) {
        if (!StringUtils.hasText(contentHash) || !contentHash.matches("(?i)^[a-f0-9]{64}$")) {
            return null;
        }
        String key = HASH_KEY_PREFIX + contentHash.toLowerCase(Locale.ROOT);
        try {
            String objectKey = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(objectKey)) {
                return null;
            }
            if (!objectExists(objectKey)) {
                redisTemplate.delete(key);
                return null;
            }
            return objectKey;
        } catch (Exception e) {
            log.warn("查询文件哈希失败: hash={}", contentHash, e);
            return null;
        }
    }

    @Override
    public void saveContentHash(String contentHash, String objectKey) {
        if (!StringUtils.hasText(contentHash) || !StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    HASH_KEY_PREFIX + contentHash.toLowerCase(Locale.ROOT),
                    objectKey,
                    FILE_HASH_TTL
            );
        } catch (Exception e) {
            log.warn("保存文件哈希失败: hash={}, key={}", contentHash, objectKey, e);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String ct = contentType.trim().toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        if ("image/jpg".equals(ct)) {
            return "image/jpeg";
        }
        return ct.isEmpty() ? null : ct;
    }

    private void assertValidPartNumber(int partNumber) {
        if (partNumber < 1 || partNumber > 32) {
            throw new IllegalArgumentException("partNumber 必须在 1~32");
        }
    }

    private String partObjectKey(String uploadId, int partNumber) {
        return "_multipart/" + uploadId + "/part-" + partNumber;
    }

    private MultipartMeta requireMultipartMeta(String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new IllegalArgumentException("uploadId 不能为空");
        }
        try {
            String json = redisTemplate.opsForValue().get(MP_META_PREFIX + uploadId);
            if (!StringUtils.hasText(json)) {
                throw new IllegalArgumentException("分片会话不存在或已过期");
            }
            Map<String, String> map = objectMapper.readValue(json, new TypeReference<>() {});
            String objectName = map.get("objectName");
            String contentType = map.get("contentType");
            if (!StringUtils.hasText(objectName) || !StringUtils.hasText(contentType)) {
                throw new IllegalArgumentException("分片会话数据损坏");
            }
            return new MultipartMeta(objectName, contentType);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("分片会话不存在或已过期");
        }
    }

    private List<PartETag> resolvePartsForComplete(String uploadId, List<PartETag> clientParts) {
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(MP_PARTS_PREFIX + uploadId);
        if (stored.isEmpty()) {
            throw new IllegalArgumentException("没有任何已上传分片");
        }
        List<PartETag> fromRedis = new ArrayList<>();
        for (Map.Entry<Object, Object> e : stored.entrySet()) {
            fromRedis.add(new PartETag(Integer.parseInt(String.valueOf(e.getKey())), String.valueOf(e.getValue())));
        }
        fromRedis.sort(Comparator.comparingInt(PartETag::partNumber));
        // 若客户端传了 parts，校验 etag 一致
        if (clientParts != null && !clientParts.isEmpty()) {
            Map<Integer, String> clientMap = new LinkedHashMap<>();
            for (PartETag p : clientParts) {
                clientMap.put(p.partNumber(), stripQuotes(p.etag()));
            }
            for (PartETag p : fromRedis) {
                String expected = clientMap.get(p.partNumber());
                if (expected != null && !expected.equals(stripQuotes(p.etag()))) {
                    throw new IllegalArgumentException("分片 etag 不匹配: part " + p.partNumber());
                }
            }
        }
        return fromRedis;
    }

    private List<PartETag> listUploadedPartsSafe(String uploadId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(MP_PARTS_PREFIX + uploadId);
        List<PartETag> parts = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            try {
                parts.add(new PartETag(Integer.parseInt(String.valueOf(e.getKey())), String.valueOf(e.getValue())));
            } catch (Exception ignored) {
                // skip
            }
        }
        return parts;
    }

    private void cleanupMultipartTemp(String uploadId, List<PartETag> parts) {
        cleanupMultipartTempObjects(uploadId, parts);
        redisTemplate.delete(MP_META_PREFIX + uploadId);
        redisTemplate.delete(MP_PARTS_PREFIX + uploadId);
    }

    /** 仅清理临时分片对象（不影响 Redis 会话） */
    private void cleanupMultipartTempObjects(String uploadId, List<PartETag> parts) {
        ObjectStorageBackend backend = objectStorageRouter.activeBackend();
        for (PartETag p : parts) {
            backend.deletePartObject(partObjectKey(uploadId, p.partNumber()));
        }
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

    private record MultipartMeta(String objectName, String contentType) {
    }
}
