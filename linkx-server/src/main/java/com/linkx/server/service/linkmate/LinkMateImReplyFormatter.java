package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵伴 IM 回复格式化：多段气泡拆分、@ 提问者。
 */
public final class LinkMateImReplyFormatter {

    /** 群聊多条回复分隔符（与提示词约定一致） */
    public static final String MULTI_MESSAGE_DELIMITER = "<<<MSG>>>";

    private static final int MAX_GROUP_BUBBLES = 4;

    private LinkMateImReplyFormatter() {
    }

    /**
     * 将模型输出拆成多条聊天气泡；无分隔符时返回单条。
     */
    public static List<String> splitGroupBubbles(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (!trimmed.contains(MULTI_MESSAGE_DELIMITER)) {
            return List.of(trimmed);
        }
        String[] parts = trimmed.split(MULTI_MESSAGE_DELIMITER);
        List<String> bubbles = new ArrayList<>();
        for (String part : parts) {
            String text = part.trim();
            if (StringUtils.hasText(text)) {
                bubbles.add(text);
            }
            if (bubbles.size() >= MAX_GROUP_BUBBLES) {
                break;
            }
        }
        return bubbles.isEmpty() ? List.of(trimmed) : bubbles;
    }

    /**
     * 群聊回复前缀 @ 提问者（已含 @ 则跳过）。
     */
    public static String withMentionPrefix(String content, String senderName) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(senderName)) {
            return content;
        }
        String name = senderName.trim();
        String mention = "@" + name;
        String trimmed = content.trim();
        if (trimmed.startsWith(mention)) {
            return trimmed;
        }
        if (trimmed.startsWith("@" + name + " ") || trimmed.startsWith("@" + name + "，")
                || trimmed.startsWith("@" + name + ",")) {
            return trimmed;
        }
        return mention + " " + trimmed;
    }
}
