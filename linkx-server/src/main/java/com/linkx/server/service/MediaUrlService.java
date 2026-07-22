package com.linkx.server.service;

import com.linkx.server.config.LinkxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将数据库中的对象 key / 旧版完整 MinIO URL 转为浏览器可访问的预签名 URL。
 * <p>
 * 按用途分级过期：头像较长、业务文件较短、分享下载最短，降低链接泄露窗口。
 * 签名 Host 与当前 linkx.minio.endpoint 一致（开发环境为 127.0.0.1）。
 */
@Service
@RequiredArgsConstructor
public class MediaUrlService {

    private final FileStorageService fileStorageService;
    private final LinkxProperties linkxProperties;

    /** 兼容旧调用：默认按头像时效签发 */
    public String resolve(String keyOrUrl) {
        return resolveAvatar(keyOrUrl);
    }

    /** 头像 / 封面 / 友链配图等展示类媒体 */
    public String resolveAvatar(String keyOrUrl) {
        return resolve(keyOrUrl, linkxProperties.getMinio().getPresignExpiry().getAvatarSeconds());
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
        // 历史占位路径：客户端并无该静态资源，签发/原样返回都会裂图，改回空让前端走文字头像
        if ("/default-avatar.svg".equals(value) || value.endsWith("/default-avatar.svg")) {
            return null;
        }
        // 本地静态资源 / data URL 不走 MinIO
        if (value.startsWith("/") || value.startsWith("data:") || value.startsWith("blob:")) {
            return value;
        }
        // 外部 http(s) 链接（非本系统 MinIO）原样返回，避免被当成 object key 签名
        if (isExternalHttpUrl(value)) {
            return value;
        }
        int seconds = expirySeconds > 0
                ? expirySeconds
                : linkxProperties.getMinio().getPresignExpiry().getAvatarSeconds();
        return fileStorageService.getPresignedUrl(value, seconds);
    }

    private boolean isExternalHttpUrl(String value) {
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return false;
        }
        String endpoint = linkxProperties.getMinio().getEndpoint();
        if (endpoint != null && !endpoint.isBlank() && value.startsWith(endpoint)) {
            return false;
        }
        // 兼容历史 localhost / 127.0.0.1 MinIO 地址
        return !(value.contains("://localhost:9000/")
                || value.contains("://127.0.0.1:9000/")
                || value.contains("://[::1]:9000/"));
    }
}
