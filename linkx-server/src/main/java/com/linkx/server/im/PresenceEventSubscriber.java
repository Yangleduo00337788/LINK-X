package com.linkx.server.im;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FriendService;
import com.linkx.server.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅 Redis presence 事件，向本机连接的好友/单聊对端推送 WS {@code presence} 帧。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final FriendService friendService;
    private final ChatService chatService;
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

            // 隐藏在线状态时不对外推送 online:true（offline 仍推，便于对方清绿点）
            if (online && !userPreferenceService.showsOnlineStatus(userId)) {
                return;
            }

            Set<Long> watchers = new LinkedHashSet<>();
            List<Long> friends = friendService.listWatcherIds(userId);
            if (friends != null) {
                watchers.addAll(friends);
            }
            List<Long> peers = chatService.listPrivatePeerIds(userId);
            if (peers != null) {
                watchers.addAll(peers);
            }
            watchers.remove(userId);
            if (watchers.isEmpty()) {
                return;
            }

            Map<String, Object> data = Map.of(
                    "userId", String.valueOf(userId),
                    "online", online
            );
            for (Long watcherId : watchers) {
                // 仅本机投递：presence 已通过 Redis 广播到各实例，避免再走集群推送造成重复
                pushService.pushToUserLocal(watcherId, "presence", data);
            }
        } catch (Exception e) {
            log.warn("处理 presence 事件失败: {}", e.getMessage());
        }
    }

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
