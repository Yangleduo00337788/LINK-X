package com.linkx.server.config.interceptor;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.BizRateLimitPolicyResolver;
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
    private final LinkxProperties linkxProperties;
    private final BizRateLimitPolicyResolver bizRateLimitPolicyResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RateLimit annotation = hm.getMethodAnnotation(RateLimit.class);
        BizRateLimitPolicyResolver.ResolvedLimit policy = bizRateLimitPolicyResolver.resolve(annotation);
        String identity;
        String scope;
        int max = policy.maxAttempts();
        int window = policy.windowSeconds();
        boolean byUser = annotation == null || annotation.byUser();

        if (annotation == null) {
            scope = "global-default";
        } else {
            scope = annotation.scope();
        }

        String clientIp = ClientIpResolver.resolve(request, linkxProperties);
        if (rateLimitService.isWhitelisted(clientIp)) {
            return true;
        }

        if (byUser) {
            Long userId = (Long) request.getAttribute("userId");
            if (userId != null) {
                identity = String.valueOf(userId);
            } else {
                // 未登录时用客户端 IP 作为限流标识，避免所有匿名用户共享同一限流桶
                identity = "ip:" + (clientIp != null ? clientIp : request.getRemoteAddr());
            }
        } else {
            identity = clientIp;
        }

        String key = "biz:" + scope + ":" + identity;
        try {
            rateLimitService.check(key, max, window);
        } catch (CustomException e) {
            log.warn("RateLimit 触发: scope={}, key={}, message={}",
                    scope, identity, e.getMessage());
            throw e;
        }
        return true;
    }
}
