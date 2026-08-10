package com.linkx.server.common.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.exception.CustomException;

import java.util.Locale;

/**
 * 管理端异步导出模块（与既有 sync /export 权限码对齐）。
 */
public enum AdminExportModule {
    USERS("users", "admin:user:export", "users"),
    DEVICES("devices", "admin:device:export", "devices"),
    BLACKLIST("blacklist", "admin:blacklist:export", "blacklist"),
    RISK_EVENTS("risk-events", "admin:risk-event:export", "risk-events"),
    REVIEWS("reviews", "admin:review:export", "reviews"),
    FEEDBACK("feedback", "admin:feedback:export", "feedback"),
    AUDIT_LOGS("audit-logs", "admin:audit:export", "audit-logs"),
    LOGIN_LOGS("login-logs", "admin:login-log:export", "login-logs"),
    STATISTICS("statistics", "admin:statistics:export", "statistics");

    private final String code;
    private final String permission;
    private final String filenamePrefix;

    AdminExportModule(String code, String permission, String filenamePrefix) {
        this.code = code;
        this.permission = permission;
        this.filenamePrefix = filenamePrefix;
    }

    public String getCode() {
        return code;
    }

    public String getPermission() {
        return permission;
    }

    public String getFilenamePrefix() {
        return filenamePrefix;
    }

    public static AdminExportModule fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CustomException(400, "export module is required");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (AdminExportModule m : values()) {
            if (m.code.equals(normalized)) {
                return m;
            }
        }
        throw new CustomException(400, "unsupported export module: " + code);
    }
}
