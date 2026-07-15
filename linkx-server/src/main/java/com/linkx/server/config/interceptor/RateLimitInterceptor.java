package com.linkx.server.config.interceptor;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 业务接口限流拦截器。
 * <p>
 * 解析 Controller 方法上的 @RateLimit 注解，按用户或 IP 维度计数。
 * 不依赖 AOP，避免引入 aspectjweaver。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RateLimit annotation = hm.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }

        String identity;
        if (annotation.byUser()) {
            Long userId = (Long) request.getAttribute("userId");
            identity = userId != null ? String.valueOf(userId) : "anonymous";
        } else {
            identity = getClientIp(request);
        }

        String key = "biz:" + annotation.scope() + ":" + identity;
        try {
            rateLimitService.check(key, annotation.value(), annotation.window());
        } catch (CustomException e) {
            log.warn("RateLimit 触发: scope={}, key={}, message={}",
                    annotation.scope(), identity, e.getMessage());
            throw e;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
