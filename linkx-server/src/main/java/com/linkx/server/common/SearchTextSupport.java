package com.linkx.server.common;


/**
 * 构建落库搜索摘要：在正文加密场景下供 FULLTEXT 索引使用。
 */
public final class SearchTextSupport {

    public static final int MAX_LEN = 2000;

    private SearchTextSupport() {
    }

    public static String buildMessageSearchText(String content, String fileName) {
        return truncate(joinNonBlank(content, fileName));
    }

    public static String buildMomentsSearchText(String content, String location) {
        return truncate(joinNonBlank(content, location));
    }

    private static String joinNonBlank(String a, String b) {
        String left = a != null ? a.trim() : "";
        String right = b != null ? b.trim() : "";
        if (left.isEmpty() && right.isEmpty()) {
            return null;
        }
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + " " + right;
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_LEN);
    }
}
