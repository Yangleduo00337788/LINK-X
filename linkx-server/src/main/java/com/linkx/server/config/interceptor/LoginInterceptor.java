package com.linkx.server.config.interceptor;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.common.TokenType;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final TokenCookieUtil tokenCookieUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // Token 读取顺序：Authorization Header（Electron）优先，Cookie（Web 环境 HttpOnly Cookie）兜底
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token)) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }
        }
        if (!StringUtils.hasText(token)) {
            String cookieToken = tokenCookieUtil.readAccessToken(request);
            if (StringUtils.hasText(cookieToken)) {
                token = cookieToken;
            }
        }
        if (!StringUtils.hasText(token)) {
            throw new CustomException(401, "未登录或登录已过期");
        }

        try {
            TokenType tokenType = jwtUtils.getTokenType(token);
            if (tokenType == TokenType.REFRESH) {
                throw new CustomException(401, "无效的访问令牌");
            }

            tokenService.assertAccessTokenActive(token, request.getHeader("X-Device-Id"));
            Long userId = jwtUtils.getUserIdFromToken(token);
            request.setAttribute("userId", userId);
            return true;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(401, "登录已过期，请重新登录");
        }
    }
}
