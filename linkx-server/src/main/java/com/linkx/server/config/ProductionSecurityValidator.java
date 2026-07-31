package com.linkx.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境启动安全加固校验（文档 §11）。
 * <p>
 * 强制 HTTPS、关闭开发模式、管理端强制 TOTP、CORS 仅 HTTPS Origin、
 * JWT/MinIO 等密钥不得为空弱值。不通过则拒绝启动。
 * </p>
 */
@Slf4j
@Component
@Profile("prod")
@Order(100)
@RequiredArgsConstructor
public class ProductionSecurityValidator implements ApplicationRunner {

    private final LinkxProperties linkxProperties;

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = new ArrayList<>();

        if (!linkxProperties.getSecurity().isRequireHttps()) {
            errors.add("REQUIRE_HTTPS 必须为 true");
        }

        Boolean devMode = linkxProperties.getApp().getDevModeEnabled();
        if (Boolean.TRUE.equals(devMode)) {
            errors.add("DEV_MODE_ENABLED 生产环境必须为 false");
        }

        if (!linkxProperties.getAuth().isAdminTotpRequired()) {
            errors.add("ADMIN_TOTP_REQUIRED 生产环境必须为 true（管理端强制双因素）");
        }

        if (!linkxProperties.getAuth().isAdminCaptchaEnabled()) {
            errors.add("ADMIN_CAPTCHA_ENABLED 生产环境必须为 true");
        }

        List<String> origins = linkxProperties.getCors().getAllowedOrigins();
        if (origins != null) {
            for (String origin : origins) {
                if (origin != null && origin.trim().startsWith("http://")) {
                    errors.add("CORS_ALLOWED_ORIGINS 生产环境不允许 http:// Origin: " + origin.trim());
                }
            }
        }

        if (!StringUtils.hasText(linkxProperties.getMinio().getAccessKey())
                || !StringUtils.hasText(linkxProperties.getMinio().getSecretKey())) {
            errors.add("MINIO_ACCESS_KEY / MINIO_SECRET_KEY 不能为空");
        }

        String minioEndpoint = linkxProperties.getMinio().getEndpoint();
        if (StringUtils.hasText(minioEndpoint) && minioEndpoint.startsWith("http://")) {
            errors.add("MINIO_ENDPOINT 生产环境应使用 https://");
        }

        if (!errors.isEmpty()) {
            String msg = "[生产安全] 启动校验失败:\n - " + String.join("\n - ", errors);
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("[生产安全] 加固校验通过：HTTPS / TOTP / 验证码 / CORS / MinIO 密钥");
    }
}
