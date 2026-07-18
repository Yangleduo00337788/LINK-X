package com.linkx.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将数据库中的对象 key / 旧版完整 MinIO URL 转为浏览器可访问的预签名 URL。
 * 签名 Host 与当前 linkx.minio.endpoint 一致（开发环境为 127.0.0.1，规避 Windows localhost→IPv6 超时）。
 */
@Service
@RequiredArgsConstructor
public class MediaUrlService {

    private static final int DEFAULT_EXPIRY = 24 * 3600;

    private final FileStorageService fileStorageService;

    public String resolve(String keyOrUrl) {
        return resolve(keyOrUrl, DEFAULT_EXPIRY);
    }

    public String resolve(String keyOrUrl, int expirySeconds) {
        if (!StringUtils.hasText(keyOrUrl)) {
            return keyOrUrl;
        }
        String value = keyOrUrl.trim();
        // 本地静态资源 / data URL 不走 MinIO
        if (value.startsWith("/") || value.startsWith("data:") || value.startsWith("blob:")) {
            return value;
        }
        return fileStorageService.getPresignedUrl(value, expirySeconds);
    }
}
