package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.storage.ObjectStorageRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将数据库中的对象 key / 旧版完整存储 URL 转为浏览器可访问的预签名 URL。
 */
@Service
@RequiredArgsConstructor
public class MediaUrlService {

    private final FileStorageService fileStorageService;
    private final LinkxProperties linkxProperties;
    private final ObjectStorageRouter objectStorageRouter;

    /** 兼容旧调用：默认按头像时效签发 */
    public String resolve(String keyOrUrl) {
        return resolveAvatar(keyOrUrl);
    }

    /** 头像 / 封面 / 友链配图等展示类媒体 */
    public String resolveAvatar(String keyOrUrl) {
        return resolve(keyOrUrl, linkxProperties.getMinio().getPresignExpiry().getAvatarSeconds());
    }

    /**
     * 用户头像：优先走同源代理 {@code /media/avatars/{userId}}，避免 Electron CSP 拦截 OSS/MinIO 预签名外链。
     */
    public String resolveUserAvatar(Long userId, String keyOrUrl) {
        if (!StringUtils.hasText(keyOrUrl)) {
            return null;
        }
        String value = keyOrUrl.trim();
        if ("/default-avatar.svg".equals(value) || value.endsWith("/default-avatar.svg")) {
            return null;
        }
        if (value.startsWith("data:") || value.startsWith("blob:")) {
            return value;
        }
        if (isExternalHttpUrl(value)) {
            return value;
        }
        if (value.startsWith("/media/avatars/")) {
            return value;
        }
        if (userId != null && userId > 0) {
            return "/media/avatars/" + userId;
        }
        return resolveAvatar(keyOrUrl);
    }

    /**
     * 朋友圈背景：优先走同源代理 {@code /media/moments-background/{userId}}，避免 Electron CSP 拦截预签名外链。
     */
    public String resolveMomentsBackground(Long userId, String keyOrUrl) {
        if (!StringUtils.hasText(keyOrUrl)) {
            return null;
        }
        String value = keyOrUrl.trim();
        if (value.startsWith("data:") || value.startsWith("blob:")) {
            return value;
        }
        if (isExternalHttpUrl(value)) {
            return value;
        }
        if (value.startsWith("/media/moments-background/")) {
            return value;
        }
        if (userId != null && userId > 0) {
            return "/media/moments-background/" + userId;
        }
        return resolveAvatar(keyOrUrl);
    }

    /** 聊天附件、群文件、网盘文件等业务文件 */
    public String resolveFile(String keyOrUrl) {
        return resolve(keyOrUrl, linkxProperties.getMinio().getPresignExpiry().getFileSeconds());
    }

    /** 外部分享下载（最短有效期） */
    public String resolveShare(String keyOrUrl) {
        return resolve(keyOrUrl, linkxProperties.getMinio().getPresignExpiry().getShareSeconds());
    }

    public String resolve(String keyOrUrl, int expirySeconds) {
        if (!StringUtils.hasText(keyOrUrl)) {
            return null;
        }
        String value = keyOrUrl.trim();
        if ("/default-avatar.svg".equals(value) || value.endsWith("/default-avatar.svg")) {
            return null;
        }
        if (value.startsWith("/") || value.startsWith("data:") || value.startsWith("blob:")) {
            return value;
        }
        if (isExternalHttpUrl(value)) {
            return value;
        }
        int seconds = expirySeconds > 0
                ? expirySeconds
                : linkxProperties.getMinio().getPresignExpiry().getAvatarSeconds();
        return fileStorageService.getPresignedUrl(value, seconds);
    }

    /** 第三方 http(s)（非本系统存储）——入库原样保留，不签发、不验属主 */
    public boolean isExternalHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            return false;
        }
        if (isKnownStorageUrl(v)) {
            return false;
        }
        return !(v.contains("://localhost:9000/")
                || v.contains("://127.0.0.1:9000/")
                || v.contains("://[::1]:9000/"));
    }

    private boolean isKnownStorageUrl(String url) {
        LinkxProperties.Minio minio = linkxProperties.getMinio();
        String endpoint = minio.getEndpoint();
        if (endpoint != null && !endpoint.isBlank() && url.startsWith(endpoint)) {
            return true;
        }
        for (String origin : objectStorageRouter.mediaOriginsForCsp()) {
            if (StringUtils.hasText(origin) && url.startsWith(origin)) {
                return true;
            }
        }
        LinkxProperties.Oss oss = linkxProperties.getOss();
        if (StringUtils.hasText(oss.getBucketName())) {
            String bucket = oss.getBucketName().trim();
            if (url.contains(bucket + ".oss-") || url.contains("/" + bucket + "/")) {
                return true;
            }
        }
        LinkxProperties.Cos cos = linkxProperties.getCos();
        if (StringUtils.hasText(cos.getBucketName())) {
            String bucket = cos.getBucketName().trim();
            if (url.contains(bucket + ".cos.") || url.contains(".myqcloud.com/" + bucket + "/")) {
                return true;
            }
        }
        return false;
    }
}
