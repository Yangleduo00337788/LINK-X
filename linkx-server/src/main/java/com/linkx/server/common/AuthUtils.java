package com.linkx.server.common;

import com.linkx.server.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 从 HTTP 请求中解析当前登录用户 ID。
 */
public final class AuthUtils {

    private AuthUtils() {
    }

    public static Long getUserId(HttpServletRequest request, JwtUtils jwtUtils) {
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr instanceof Long userId) {
            return userId;
        }

        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            return jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    public static Long requireUserId(HttpServletRequest request, JwtUtils jwtUtils) {
        Long userId = getUserId(request, jwtUtils);
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        return userId;
    }
}
