package com.linkx.server.common;

import com.linkx.server.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 HTTP 请求中解析当前登录用户 ID。
 * <p>
 * 仅信任 LoginInterceptor 写入的 {@code userId} 属性（已含 JWT 类型/吊销/踢人校验），
 * 禁止在此兜底解析 Authorization，避免绕过 Redis 存活检查。
 */
public final class AuthUtils {

    private AuthUtils() {
    }

    public static Long getUserId(HttpServletRequest request, JwtUtils jwtUtils) {
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr instanceof Long userId) {
            return userId;
        }
        return null;
    }

    public static Long requireUserId(HttpServletRequest request, JwtUtils jwtUtils) {
        Long userId = getUserId(request, jwtUtils);
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        return userId;
    }
}
