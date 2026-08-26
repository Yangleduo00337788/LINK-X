package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
public final class ShortVideoTranscodeSupport {

    private ShortVideoTranscodeSupport() {
    }

    public static boolean isActive(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase();
        return "pending".equals(normalized) || "processing".equals(normalized);
    }

    public static boolean isFailed(String status) {
        return status != null && "failed".equalsIgnoreCase(status.trim());
    }

    public static boolean shouldShowStatus(String status) {
        return isActive(status) || isFailed(status);
    }
}
