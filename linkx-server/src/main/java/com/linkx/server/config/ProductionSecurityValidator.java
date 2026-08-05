package com.linkx.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境启动安全加固校验（文档 §11）。
 * <p>
 * 强制 HTTPS、关闭开发模式、管理端强制 TOTP、CORS 仅 HTTPS Origin、
 * JWT/DB/Redis/MinIO 等密钥不得为空或弱值。不通过则拒绝启动。
 * </p>
 */
@Slf4j
@Component
@Profile("prod")
@Order(100)
@RequiredArgsConstructor
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final int MIN_JWT_LENGTH = 32;
    private static final int MIN_DB_PASSWORD_LENGTH = 8;
    private static final int MIN_REDIS_PASSWORD_LENGTH = 8;
    private static final int MIN_MINIO_SECRET_LENGTH = 8;

    private final LinkxProperties linkxProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = collectErrors();
        if (!errors.isEmpty()) {
            String msg = "[生产安全] 启动校验失败:\n - " + String.join("\n - ", errors);
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.info("[生产安全] 加固校验通过：HTTPS / TOTP / 验证码 / CORS / 密钥强度");
    }

    /** 供单测直接断言校验项。 */
    List<String> collectErrors() {
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

        String jwtSecret = linkxProperties.getJwt().getSecret();
        if (ProductionSecretRules.isWeakSecret(jwtSecret, MIN_JWT_LENGTH)) {
            errors.add("JWT_SECRET 不能为空、过短（<" + MIN_JWT_LENGTH + "）或使用常见弱值/占位符");
        }

        String dbPassword = firstNonBlank(
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("DB_PASSWORD"));
        if (ProductionSecretRules.isWeakSecret(dbPassword, MIN_DB_PASSWORD_LENGTH)) {
            errors.add("DB_PASSWORD 不能为空、过短（<" + MIN_DB_PASSWORD_LENGTH + "）或使用常见弱值/占位符");
        }

        String redisPassword = firstNonBlank(
                environment.getProperty("spring.data.redis.password"),
                environment.getProperty("REDIS_PASSWORD"));
        if (ProductionSecretRules.isWeakSecret(redisPassword, MIN_REDIS_PASSWORD_LENGTH)) {
            errors.add("REDIS_PASSWORD 不能为空、过短（<" + MIN_REDIS_PASSWORD_LENGTH + "）或使用常见弱值/占位符");
        }

        String minioAccess = linkxProperties.getMinio().getAccessKey();
        String minioSecret = linkxProperties.getMinio().getSecretKey();
        if (ProductionSecretRules.isBlank(minioAccess)
                || ProductionSecretRules.isWeakSecret(minioSecret, MIN_MINIO_SECRET_LENGTH)
                || "minioadmin".equalsIgnoreCase(trim(minioAccess))) {
            errors.add("MINIO_ACCESS_KEY / MINIO_SECRET_KEY 不能为空、使用默认 minioadmin 或弱密钥");
        }

        String minioEndpoint = linkxProperties.getMinio().getEndpoint();
        if (StringUtils.hasText(minioEndpoint) && minioEndpoint.startsWith("http://")) {
            errors.add("MINIO_ENDPOINT 生产环境应使用 https://");
        }

        if (isSnailJobEnabled()) {
            String token = firstNonBlank(
                    environment.getProperty("snail-job.token"),
                    environment.getProperty("SNAIL_JOB_TOKEN"));
            if (ProductionSecretRules.isWeakSecret(token, 16)) {
                errors.add("SNAIL_JOB_TOKEN 在启用调度客户端时不能为空、过短（<16）或使用常见弱值");
            }
            if ("SJ_Wyz3dmsdbDOkDujOTSSoBjGQP1BMsVnj".equals(trim(token))) {
                errors.add("SNAIL_JOB_TOKEN 不得使用仓库示例默认值");
            }
        }

        return errors;
    }

    private boolean isSnailJobEnabled() {
        String enabled = firstNonBlank(
                environment.getProperty("snail-job.enabled"),
                environment.getProperty("SNAIL_JOB_ENABLED"));
        return Boolean.parseBoolean(enabled);
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        if (StringUtils.hasText(b)) {
            return b;
        }
        return a;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
