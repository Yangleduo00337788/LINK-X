package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
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

    private static final String HASH_KEY_PREFIX = "linkx:filehash:";
    private static final String MP_META_PREFIX = "linkx:mp:meta:";
    private static final String MP_PARTS_PREFIX = "linkx:mp:parts:";

    private final MinioClient minioClient;
    private final LinkxProperties linkxProperties;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(raw, digest)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(fullObjectName)
                                .stream(dis, file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
            }

            String contentHash = HexFormat.of().formatHex(digest.digest());
            saveContentHash(contentHash, fullObjectName);

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
            // 不打印完整签名链，避免日志泄露
            log.error("生成签名 URL 失败: key={}, err={}", key, e.getMessage());
            return null;
        }
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
            String bucketName = linkxProperties.getMinio().getBucketName();
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(key).build()
            );
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(key).build()
            );
            String contentType = stat.contentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }
            return new StoredObject(stream, contentType, stat.size(), key);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("打开 MinIO 对象失败: key={}, err={}", key, e.getMessage());
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
            String bucketName = linkxProperties.getMinio().getBucketName();
            ObjectWriteResponse resp = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(partObjectKey)
                            .stream(data, partSize, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            String etag = resp != null && StringUtils.hasText(resp.etag())
                    ? stripQuotes(resp.etag())
                    : DigestUtils.md5DigestAsHex((uploadId + "-" + partNumber + "-" + partSize).getBytes());
            redisTemplate.opsForHash().put(MP_PARTS_PREFIX + uploadId, String.valueOf(partNumber), etag);
            redisTemplate.expire(MP_PARTS_PREFIX + uploadId, MULTIPART_TTL);
            redisTemplate.expire(MP_META_PREFIX + uploadId, MULTIPART_TTL);
            return etag;
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
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

        String bucketName = linkxProperties.getMinio().getBucketName();
        try {
            // 校验中间片大小（Compose 约束）
            for (int i = 0; i < ordered.size() - 1; i++) {
                PartETag p = ordered.get(i);
                StatObjectResponse st = minioClient.statObject(
                        StatObjectArgs.builder().bucket(bucketName).object(partObjectKey(uploadId, p.partNumber())).build()
                );
                if (st.size() < MIN_COMPOSE_PART_BYTES) {
                    throw new IllegalArgumentException("分片 " + p.partNumber() + " 小于 5MB，无法合并（除最后一片外）");
                }
            }

            List<ComposeSource> sources = new ArrayList<>(ordered.size());
            for (PartETag p : ordered) {
                sources.add(ComposeSource.builder()
                        .bucket(bucketName)
                        .object(partObjectKey(uploadId, p.partNumber()))
                        .build());
            }
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .sources(sources)
                            .headers(Map.of("Content-Type", meta.contentType()))
                            .build()
            );

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
        try {
            List<PartETag> parts = listUploadedPartsSafe(uploadId);
            cleanupMultipartTemp(uploadId, parts);
        } catch (Exception e) {
            log.warn("取消分片上传清理失败: uploadId={}", uploadId, e);
            redisTemplate.delete(MP_META_PREFIX + uploadId);
            redisTemplate.delete(MP_PARTS_PREFIX + uploadId);
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
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(linkxProperties.getMinio().getBucketName())
                            .object(key)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 文件秒传/去重 ====================

    @Override
    public String findByContentHash(String contentHash) {
        if (!StringUtils.hasText(contentHash) || !contentHash.matches("(?i)^[a-f0-9]{64}$")) {
            return null;
        }
        try {
            String objectKey = redisTemplate.opsForValue().get(HASH_KEY_PREFIX + contentHash.toLowerCase(Locale.ROOT));
            if (!StringUtils.hasText(objectKey)) {
                return null;
            }
            if (!objectExists(objectKey)) {
                redisTemplate.delete(HASH_KEY_PREFIX + contentHash.toLowerCase(Locale.ROOT));
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
        if ("image/jpg".equals(ct)) {
            return "image/jpeg";
        }
        return ct;
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
        String bucketName = linkxProperties.getMinio().getBucketName();
        for (PartETag p : parts) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(partObjectKey(uploadId, p.partNumber()))
                                .build()
                );
            } catch (Exception e) {
                log.warn("清理临时分片失败: uploadId={}, part={}", uploadId, p.partNumber(), e);
            }
        }
        redisTemplate.delete(MP_META_PREFIX + uploadId);
        redisTemplate.delete(MP_PARTS_PREFIX + uploadId);
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
