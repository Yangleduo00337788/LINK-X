package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.interceptor.LoginInterceptor;
import com.linkx.server.config.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

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
    private final LocaleChangeInterceptor localeChangeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Swagger 中英文：?lang=zh_CN|en → Cookie LINKX_LANG（须先于业务拦截器）
        registry.addInterceptor(localeChangeInterceptor)
                .addPathPatterns("/**")
                .order(0);

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
                        "/auth/send-register-code",
                        "/auth/send-reset-code",
                        "/auth/verify-reset-code",
                        "/auth/reset-password-by-email",
                        // 管理端匿名鉴权接口
                        "/admin/auth/login",
                        "/admin/auth/login/totp",
                        "/admin/auth/totp/setup-challenge",
                        "/admin/auth/totp/confirm-challenge",
                        "/admin/auth/refresh",
                        "/admin/auth/logout",
                        "/admin/auth/config",
                        "/admin/auth/captcha",
                        "/cloud/share/**",
                        // 健康检查 / 版本探测（客户端未登录前需可访问）
                        "/health",
                        "/health/**",
                        "/app/version",
                        "/app/installer",
                        "/app/banners",
                        "/app/recommends",
                        "/app/activities",
                        "/app/homepage",
                        // Swagger / OpenAPI（生产用 SPRINGDOC_ENABLED=false 关闭；开发可匿名访问）
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/swagger-i18n.js",
                        // Actuator：仅放行健康探针，禁止 metrics/prometheus 匿名
                        "/actuator/health",
                        "/actuator/health/**",
                        // 外链 / 头像媒体代理（签名或公开读，供 <img> 无 Authorization 加载）
                        "/media/external",
                        "/media/avatars/**",
                        "/media/moments-background/**",
                        "/media/banners/**",
                        "/media/recommends/**",
                        "/media/activities/**",
                        "/media/stored",
                        "/error"
                );
        // 密码重置三端点保留 Login 排除（匿名可访问），但不排除 RateLimit：
        // 实际限流由 AuthController 内 rateLimitService.check 执行（拦截器仅识别 @RateLimit）。
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/captcha",
                        "/auth/config",
                        "/admin/auth/login",
                        "/admin/auth/login/totp",
                        "/admin/auth/totp/setup-challenge",
                        "/admin/auth/totp/confirm-challenge",
                        "/admin/auth/refresh",
                        "/admin/auth/logout",
                        "/admin/auth/config",
                        "/admin/auth/captcha",
                        "/health",
                        "/health/**",
                        "/app/version",
                        "/app/installer",
                        "/app/banners",
                        "/app/recommends",
                        "/app/activities",
                        "/app/homepage",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/swagger-i18n.js",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 跨域由 {@link LinkxCorsFilter} 统一处理（含 OPTIONS 预检与分片上传直连 8080）
        var origins = linkxProperties.getCors().getAllowedOrigins();
        if (CollectionUtils.isEmpty(origins)) {
            log.warn("CORS allowed-origins 未配置，LinkxCorsFilter 仅放行 local 下 localhost/127.0.0.1 任意端口");
        }
    }
}
