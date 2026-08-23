package com.linkx.server.common;


/**
 * SQL LIKE 模式安全处理：转义通配符，避免用户输入 {@code %} / {@code _} 导致全表匹配。
 */
public final class SqlLikeUtils {

    private SqlLikeUtils() {
    }

    public static String escapeLike(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code %escaped%}，用于原生 SQL {@code LIKE ?} 参数。 */
    public static String containsPattern(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return "%" + escapeLike(raw) + "%";
    }

    /** 是否仅由 LIKE 通配符组成（如 {@code %}、{@code __}）。 */
    public static boolean isWildcardOnly(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '%' && c != '_' && c != '\\') {
                return false;
            }
        }
        return true;
    }
}
