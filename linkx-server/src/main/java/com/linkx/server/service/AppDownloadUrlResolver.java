package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.storage.ObjectStorageRouter;
import com.linkx.server.storage.StorageProviderType;
import com.linkx.server.storage.impl.R2ObjectStorageBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将版本发布记录中的下载地址（对象 key / 外链 / 代理路径）解析为客户端可访问的 URL。
 */
@Service
@RequiredArgsConstructor
public class AppDownloadUrlResolver {

    /** 安装包代理链接默认 7 天有效 */
    private static final int INSTALLER_PROXY_EXPIRY_SECONDS = 7 * 24 * 3600;
    private static final int ADMIN_PREVIEW_EXPIRY_SECONDS = 3600;

    private final MediaUrlService mediaUrlService;
    private final StoredMediaProxyService storedMediaProxyService;
    private final ObjectStorageRouter objectStorageRouter;

    public String resolveForClient(String downloadUrlOrKey) {
        return resolve(downloadUrlOrKey, INSTALLER_PROXY_EXPIRY_SECONDS);
    }

    public String resolveForAdmin(String downloadUrlOrKey) {
        return resolve(downloadUrlOrKey, ADMIN_PREVIEW_EXPIRY_SECONDS);
    }

    private String resolve(String downloadUrlOrKey, int proxyExpirySeconds) {
        if (!StringUtils.hasText(downloadUrlOrKey)) {
            return "";
        }
        String value = downloadUrlOrKey.trim();
        if (value.startsWith("/media/stored")) {
            return value;
        }
        if (mediaUrlService.isExternalHttpUrl(value)) {
            return value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("/")) {
            return value;
        }
        if (value.startsWith("releases/")) {
            String direct = tryResolveR2DirectUrl(value, proxyExpirySeconds);
            if (StringUtils.hasText(direct)) {
                return direct;
            }
            return storedMediaProxyService.wrapObjectKey(value, proxyExpirySeconds);
        }
        return mediaUrlService.resolve(value, proxyExpirySeconds);
    }

    private String tryResolveR2DirectUrl(String objectKey, int expirySeconds) {
        if (objectStorageRouter.activeProvider() != StorageProviderType.R2) {
            return null;
        }
        if (objectStorageRouter.activeBackend() instanceof R2ObjectStorageBackend r2) {
            return r2.resolveDirectDownloadUrl(objectKey, expirySeconds);
        }
        return null;
    }

    public boolean isStoredObjectKey(String downloadUrlOrKey) {
        if (!StringUtils.hasText(downloadUrlOrKey)) {
            return false;
        }
        String value = downloadUrlOrKey.trim();
        return !value.startsWith("http://")
                && !value.startsWith("https://")
                && !value.startsWith("/")
                && !mediaUrlService.isExternalHttpUrl(value);
    }
}
