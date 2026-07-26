package com.linkx.server.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订阅跨实例 IM 帧推送：他机发来的帧在本机 Channel 上投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImClusterPushSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final PresenceService presenceService;
    private final ImMessagePushService pushService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(body);
            String origin = node.path("origin").asText("");
            if (presenceService.getInstanceId().equals(origin)) {
                return;
            }
            Long userId = readUserId(node.path("userId"));
            String frame = node.path("frame").asText(null);
            if (userId == null || frame == null || frame.isBlank()) {
                return;
            }
            pushService.deliverLocal(userId, frame);
        } catch (Exception e) {
            log.warn("处理跨实例 IM 推送失败: {}", e.getMessage());
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
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
