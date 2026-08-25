package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 短视频描述中的 #话题 解析与规范化。 */
public final class ShortVideoHashtagSupport {

    private static final Pattern HASHTAG = Pattern.compile("[#＃]([\\u4e00-\\u9fa5a-zA-Z0-9_]{1,32})");
    private static final int MAX_TAGS = 10;

    private ShortVideoHashtagSupport() {
    }

    public static List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG.matcher(text);
        while (matcher.find() && tags.size() < MAX_TAGS) {
            String normalized = normalize(matcher.group(1));
            if (!normalized.isBlank()) {
                tags.add(normalized);
            }
        }
        return new ArrayList<>(tags);
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 32) {
            return "";
        }
        if (trimmed.chars().allMatch(c -> c <= 127)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }
}
