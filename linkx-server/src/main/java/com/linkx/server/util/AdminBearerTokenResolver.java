package com.linkx.server.util;

import com.linkx.server.common.TokenCookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 管理端 access token 读取：Authorization Header（兼容脚本/Electron）优先，HttpOnly Cookie 兜底。
 */
public final class AdminBearerTokenResolver {

    private AdminBearerTokenResolver() {
    }

    public static String resolve(HttpServletRequest request, TokenCookieUtil tokenCookieUtil) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        if (tokenCookieUtil != null) {
            String cookieToken = tokenCookieUtil.readAccessToken(request);
            if (StringUtils.hasText(cookieToken)) {
                return cookieToken.trim();
            }
        }
        return null;
    }
}
