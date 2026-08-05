package com.linkx.server.util;

/**
 * 管理端 API 安全过滤器共用的路径排除规则。
 */
public final class AdminApiSecurityPaths {

    private AdminApiSecurityPaths() {
    }

    public static boolean isAdminApi(String uri) {
        return uri != null && uri.contains("/admin/");
    }

    /** 签名与加解密过滤器：基础设施路径 */
    public static boolean isInfraExcludedPath(String uri) {
        if (uri == null) {
            return true;
        }
        return uri.contains("/admin/events/stream")
                || uri.contains("/swagger-ui")
                || uri.contains("/v3/api-docs")
                || uri.contains("/actuator/")
                || uri.contains("/health")
                || uri.contains("/media/");
    }

    /** 登录引导接口：不验签、不加解密 */
    public static boolean isAuthBootstrapPath(String uri) {
        if (uri == null) {
            return true;
        }
        return uri.contains("/admin/auth/config")
                || uri.contains("/admin/auth/login")
                || uri.contains("/admin/auth/login/totp")
                || uri.contains("/admin/auth/totp/setup-challenge")
                || uri.contains("/admin/auth/totp/confirm-challenge")
                || uri.contains("/admin/auth/refresh")
                || uri.contains("/admin/auth/logout");
    }

    /** HMAC 签名过滤器跳过 */
    public static boolean isSignExcludedPath(String uri) {
        return isAuthBootstrapPath(uri) || isInfraExcludedPath(uri);
    }

    /** 加解密过滤器 / 响应加密跳过（登录引导 + SSE 等） */
    public static boolean isEncryptExcludedPath(String uri) {
        return isAuthBootstrapPath(uri) || isInfraExcludedPath(uri);
    }

    public static boolean isBinaryEncryptResponsePath(String uri) {
        if (uri == null || isEncryptExcludedPath(uri)) {
            return false;
        }
        return uri.contains("/export")
                || uri.contains("/download");
    }
}
