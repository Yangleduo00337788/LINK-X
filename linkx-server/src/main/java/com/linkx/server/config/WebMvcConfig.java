package com.linkx.server.config;

import com.linkx.server.config.interceptor.LoginInterceptor;
import com.linkx.server.config.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：拦截器与 CORS
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final LinkxProperties linkxProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // context-path=/api 时，DispatcherServlet 内路径不含 /api 前缀，故用 /**
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/captcha",
                        "/auth/config",
                        // /auth/reset-password 需登录（拦截器写入 userId），不可排除
                        "/auth/send-reset-code",
                        "/auth/verify-reset-code",
                        "/auth/reset-password-by-email",
                        "/cloud/share/**",
                        // 健康检查 / 版本探测（客户端未登录前需可访问）
                        "/health",
                        "/health/**",
                        "/app/version",
                        // Swagger / OpenAPI（生产用 SPRINGDOC_ENABLED=false 关闭；开发可匿名访问）
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        // Actuator：仅放行健康探针，禁止 metrics/prometheus 匿名
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/error"
                );
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/captcha",
                        "/auth/config",
                        "/auth/send-reset-code",
                        "/auth/verify-reset-code",
                        "/auth/reset-password-by-email",
                        "/health",
                        "/health/**",
                        "/app/version",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = linkxProperties.getCors().getAllowedOrigins();
        if (CollectionUtils.isEmpty(origins)) {
            // 未配置白名单时：拒绝所有跨域请求，避免误放行。
            // Electron 桌面客户端走 file:// 协议不受 CORS 限制；
            // 开发时可通过 linkx.cors.allowed-origins 显式配置本地开发地址。
            log.warn("CORS allowed-origins 未配置，所有跨域请求将被拒绝（仅同源 / Electron 客户端可用）");
            return;
        }
        registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // 严格限制 Headers，避免通配
                .allowedHeaders(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "Origin",
                        "User-Agent",
                        "X-Requested-With",
                        // 客户端 apiClient 拦截器对每个请求都会带上设备头，须放行否则预检失败（验证码等匿名接口也会挂）
                        "X-Device-Id",
                        "X-Device-Name",
                        "X-Device-Type"
                )
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600)
                .allowedOrigins(origins.toArray(String[]::new));
    }
}
