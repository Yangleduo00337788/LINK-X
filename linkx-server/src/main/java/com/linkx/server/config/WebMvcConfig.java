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
        String api = "/api";

        // 1. 登录拦截器：先校验 JWT
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(api + "/**")
                .excludePathPatterns(
                        api + "/auth/login",
                        api + "/auth/register",
                        api + "/auth/refresh",
                        api + "/auth/logout",
                        api + "/auth/captcha",
                        api + "/auth/reset-password",
                        api + "/auth/send-reset-code",
                        api + "/auth/verify-reset-code",
                        api + "/auth/reset-password-by-email",
                        api + "/cloud/share/**",
                        // Swagger / OpenAPI
                        api + "/swagger-ui/**",
                        api + "/swagger-ui.html",
                        api + "/v3/api-docs/**",
                        api + "/webjars/**",
                        // Actuator
                        api + "/actuator/**",
                        "/actuator/**",
                        "/error"
                );
        // 2. 限流拦截器：在登录拦截器之后执行（需要 userId attribute）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(api + "/**")
                .excludePathPatterns(
                        api + "/auth/login",
                        api + "/auth/register",
                        api + "/auth/refresh",
                        api + "/auth/logout",
                        api + "/auth/captcha",
                        api + "/auth/reset-password",
                        api + "/auth/send-reset-code",
                        api + "/auth/verify-reset-code",
                        api + "/auth/reset-password-by-email",
                        api + "/cloud/share/**",
                        // Swagger / OpenAPI
                        api + "/swagger-ui/**",
                        api + "/swagger-ui.html",
                        api + "/v3/api-docs/**",
                        api + "/webjars/**",
                        // Actuator
                        api + "/actuator/**",
                        "/actuator/**",
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
                        "X-Requested-With"
                )
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600)
                .allowedOrigins(origins.toArray(String[]::new));
    }
}
