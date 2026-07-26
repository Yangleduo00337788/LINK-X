package com.linkx.server.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.service.FriendService;
import com.linkx.server.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 订阅 Redis presence 事件，向本机在线的反向好友推送 WS {@code presence} 帧。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final FriendService friendService;
    private final UserPreferenceService userPreferenceService;
    private final ImMessagePushService pushService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(body);
            Long userId = readUserId(node.path("userId"));
            if (userId == null) {
                log.debug("忽略无效 presence 事件: {}", body);
                return;
            }
            boolean online = node.path("online").asBoolean(false);

            // 隐藏在线状态时不对外推送 online:true
            if (online && !userPreferenceService.showsOnlineStatus(userId)) {
                return;
            }

            List<Long> watchers = friendService.listWatcherIds(userId);
            if (watchers.isEmpty()) {
                return;
            }

            // userId 用字符串，避免前端 JS 雪花精度丢失（与全局 Jackson Long→String 一致）
            Map<String, Object> data = Map.of(
                    "userId", String.valueOf(userId),
                    "online", online
            );
            for (Long watcherId : watchers) {
                pushService.pushToUser(watcherId, "presence", data);
            }
        } catch (Exception e) {
            log.warn("处理 presence 事件失败: {}", e.getMessage());
        }
    }

    /**
     * 兼容数字与字符串 userId（全局 Jackson 将 Long 写成字符串）。
     */
    private static Long readUserId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
