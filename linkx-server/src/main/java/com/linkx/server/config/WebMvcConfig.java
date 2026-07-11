package com.linkx.server.config;

import com.linkx.server.config.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final LinkxProperties linkxProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/captcha",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var cors = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        if (!CollectionUtils.isEmpty(linkxProperties.getCors().getAllowedOrigins())) {
            cors.allowedOrigins(linkxProperties.getCors().getAllowedOrigins().toArray(String[]::new));
        } else {
            cors.allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
        }
    }
}
