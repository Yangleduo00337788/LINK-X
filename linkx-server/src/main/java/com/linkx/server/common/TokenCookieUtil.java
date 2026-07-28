package com.linkx.server.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Token Cookie 工具类。
 * <p>
 * Web 环境下 Access/Refresh Token 通过 HttpOnly + Secure + SameSite=Lax 的 Cookie 下发，
 * 避免 XSS 直接窃取（JS 无法读取 HttpOnly Cookie）。Electron 桌面环境仍走 Authorization
 * Header + safeStorage 落盘，本工具只负责 Web Cookie 读写，不影响 Electron 流程。
 * </p>
 * <p>
 * CSRF 防护：SameSite=Lax 模式下，跨站 POST 请求浏览器不会自动携带 Cookie，基本阻断 CSRF；
 * 配合后端 CORS Origin 白名单，可覆盖绝大多数场景。
 * </p>
 */
@Component
public class TokenCookieUtil {

    /** Access Token Cookie 名 */
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    /** Refresh Token Cookie 名 */
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    /**
     * Cookie Path：设为根路径 {@code /}，使 Cookie 同时覆盖 HTTP API（{@code /api}）
     * 与 WebSocket（{@code /ws}，独立 Netty 端口）。若设为 {@code /api}，浏览器不会把 Cookie
     * 带给 {@code /ws} 路径，导致 Web 环境 WebSocket 无法凭 Cookie 鉴权。
     */
    public static final String COOKIE_PATH = "/";
    /** SameSite 策略：Lax 可防绝大多数 CSRF POST 自动携带 */
    private static final String SAME_SITE = "Lax";

    /**
     * 下发 Access/Refresh Token Cookie。
     *
     * @param response          HTTP 响应
     * @param accessToken       访问令牌（为空则不下发该 Cookie）
     * @param refreshToken      刷新令牌（为空则不下发该 Cookie）
     * @param accessMaxAgeSec   Access Cookie 有效期（秒）
     * @param refreshMaxAgeSec  Refresh Cookie 有效期（秒）
     * @param secure            是否标记 Secure（仅 HTTPS 下可设，HTTP 设了浏览器会丢弃）
     */
    public void setTokenCookies(HttpServletResponse response,
                                String accessToken, String refreshToken,
                                long accessMaxAgeSec, long refreshMaxAgeSec,
                                boolean secure) {
        if (accessToken != null && !accessToken.isBlank()) {
            response.addHeader("Set-Cookie", buildCookie(
                    ACCESS_TOKEN_COOKIE, accessToken, accessMaxAgeSec, secure).toString());
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            response.addHeader("Set-Cookie", buildCookie(
                    REFRESH_TOKEN_COOKIE, refreshToken, refreshMaxAgeSec, secure).toString());
        }
    }

    /**
     * 清除 Access/Refresh Token Cookie（Max-Age=0 立即失效）。
     * 无论是否曾下发，清除都是幂等的。
     */
    public void clearTokenCookies(HttpServletResponse response, boolean secure) {
        response.addHeader("Set-Cookie", buildCookie(
                ACCESS_TOKEN_COOKIE, "", 0, secure).toString());
        response.addHeader("Set-Cookie", buildCookie(
                REFRESH_TOKEN_COOKIE, "", 0, secure).toString());
    }

    /**
     * 从 Servlet 请求读取 Access Token Cookie。
     *
     * @return token 值；不存在或为空返回 null
     */
    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_TOKEN_COOKIE);
    }

    /**
     * 从 Servlet 请求读取 Refresh Token Cookie。
     *
     * @return token 值；不存在或为空返回 null
     */
    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    /**
     * 从原始 Cookie 请求头解析指定 Cookie 值。
     * 供 Netty WebSocket 握手等非 Servlet 场景复用，与 Servlet 读取逻辑保持一致。
     *
     * @param cookieHeader Cookie 请求头原始值（如 "a=1; b=2"）
     * @param name         目标 Cookie 名
     * @return 值；不存在或为空返回 null
     */
    public static String parseCookie(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isBlank() || name == null) {
            return null;
        }
        for (String pair : cookieHeader.split(";")) {
            String trimmed = pair.trim();
            int idx = trimmed.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = trimmed.substring(0, idx).trim();
            if (name.equals(key)) {
                String value = trimmed.substring(idx + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeSec, boolean secure) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(maxAgeSec)
                .build();
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isEmpty()) ? null : value;
            }
        }
        return null;
    }
}
