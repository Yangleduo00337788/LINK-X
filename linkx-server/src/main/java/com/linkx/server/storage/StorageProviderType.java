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
    LOCAL;

    public static StorageProviderType fromWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return MINIO;
        }
        String normalized = wire.trim().toLowerCase();
        switch (normalized) {
            case "oss":
                return OSS;
            case "local":
                return LOCAL;
            case "minio":
            default:
                return MINIO;
        }
    }

    public String toWire() {
        return name().toLowerCase();
    }
}
