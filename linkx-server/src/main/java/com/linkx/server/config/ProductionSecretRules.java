package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 生产环境密钥/口令弱值判定（文档 §11）。
 */
public final class ProductionSecretRules {

    private static final Set<String> WEAK_SECRETS = Set.of(
            "changeme",
            "changeit",
            "password",
            "password123",
            "passw0rd",
            "secret",
            "secretkey",
            "jwtsecret",
            "jwt_secret",
            "linkx",
            "linkx-secret",
            "admin",
            "root",
            "123456",
            "12345678",
            "qwerty",
            "test",
            "testing",
            "default",
            "minioadmin",
            "your-secret",
            "your_secret",
            "replace-me",
            "todo"
    );

    private ProductionSecretRules() {
    }

    public static boolean isBlank(String value) {
        return !StringUtils.hasText(value);
    }

    /** 空值、过短或命中常见占位/弱口令视为不安全。 */
    public static boolean isWeakSecret(String value, int minLength) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.length() < minLength) {
            return true;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (WEAK_SECRETS.contains(normalized)) {
            return true;
        }
        // 纯重复字符，如 aaaaaaaa
        if (normalized.chars().distinct().count() <= 2) {
            return true;
        }
        return false;
    }
}
