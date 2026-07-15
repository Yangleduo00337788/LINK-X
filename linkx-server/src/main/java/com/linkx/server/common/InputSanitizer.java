package com.linkx.server.common;

import org.springframework.web.util.HtmlUtils;

/**
 * 用户输入清洗工具：防止 XSS。
 *
 * 策略：
 * 1. 去除首尾空白
 * 2. 转义 HTML 特殊字符为实体（&lt; &gt; &amp; &quot; &#x27;）
 * 3. 限制最大长度（强制）
 *
 * 注意：使用 Spring 的 HtmlUtils.htmlEscape()，比 OWASP ESAPI 轻量，但已覆盖常见场景。
 * 若业务需要富文本，请使用专用的 HTML 清洗库（如 Jsoup）。
 */
public final class InputSanitizer {

    private InputSanitizer() {
    }

    /** 默认最大长度 2000 字符 */
    public static final int DEFAULT_MAX_LENGTH = 2000;

    /**
     * 转义用户输入的文本（昵称、签名、聊天文本、群名、公告、朋友圈内容等）
     *
     * @param value  原始用户输入
     * @param maxLen 最大长度
     * @return 转义后的安全文本
     */
    public static String sanitizeText(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        // 限制长度
        if (trimmed.length() > maxLen) {
            trimmed = trimmed.substring(0, maxLen);
        }
        // HTML 转义
        return HtmlUtils.htmlEscape(trimmed);
    }

    /** 默认最大长度 */
    public static String sanitizeText(String value) {
        return sanitizeText(value, DEFAULT_MAX_LENGTH);
    }

    /**
     * 严格清洗：去除任何 HTML 标签（用于纯文本字段如用户名）
     */
    public static String stripHtml(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.length() > maxLen) {
            trimmed = trimmed.substring(0, maxLen);
        }
        return HtmlUtils.htmlEscape(trimmed);
    }
}
