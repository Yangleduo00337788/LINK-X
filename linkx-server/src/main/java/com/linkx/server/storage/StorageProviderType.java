package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import org.springframework.util.StringUtils;

/**
 * 对象存储提供商。
 */
public enum StorageProviderType {
    MINIO,
    OSS,
    COS,
    R2;

    public static StorageProviderType fromWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return MINIO;
        }
        String normalized = wire.trim().toLowerCase();
        switch (normalized) {
            case "oss":
                return OSS;
            case "cos":
                return COS;
            case "r2":
                return R2;
            case "local":
                // 已移除本地存储，历史配置回退 MinIO
                return MINIO;
            case "minio":
            default:
                return MINIO;
        }
    }

    public String toWire() {
        return name().toLowerCase();
    }
}
