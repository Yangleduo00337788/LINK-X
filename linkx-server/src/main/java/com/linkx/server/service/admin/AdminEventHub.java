package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管理端 SSE 连接池：订阅 Redis 管理事件并扇出到在线管理员浏览器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminEventHub implements MessageListener {

    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;

    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final List<AdminSseConnection> connections = new CopyOnWriteArrayList<>();
    private RedisMessageListenerContainer container;

    @PostConstruct
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this, new ChannelTopic(AdminEventPublisher.CHANNEL));
        container.afterPropertiesSet();
        container.start();
        log.info("管理端事件 SSE Hub 已启动: channel={}", AdminEventPublisher.CHANNEL);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            try {
                container.stop();
            } catch (Exception ignored) {
                // ignore
            }
        }
        for (AdminSseConnection conn : connections) {
            try {
                conn.emitter().complete();
            } catch (Exception ignored) {
                // ignore
            }
        }
        connections.clear();
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AdminSseConnection conn = new AdminSseConnection(userId, emitter);
        connections.add(conn);
        Runnable remove = () -> connections.remove(conn);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"ok\":true}"));
        } catch (IOException e) {
            connections.remove(conn);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        fanout(payload);
    }

    private void fanout(String payload) {
        Set<Long> targets = parseTargetUserIds(payload);
        for (AdminSseConnection conn : connections) {
            if (targets != null && !targets.isEmpty()) {
                Long userId = conn.userId();
                if (userId == null || !targets.contains(userId)) {
                    continue;
                }
            }
            try {
                conn.emitter().send(SseEmitter.event().name("admin_event").data(payload));
            } catch (Exception e) {
                connections.remove(conn);
                try {
                    conn.emitter().complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private Set<Long> parseTargetUserIds(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode arr = root.get("targetUserIds");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return null;
            }
            Set<Long> ids = new HashSet<>();
            for (JsonNode node : arr) {
                if (node.isNumber()) {
                    ids.add(node.longValue());
                } else if (node.isTextual()) {
                    try {
                        ids.add(Long.parseLong(node.asText()));
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
            }
            return ids.isEmpty() ? null : ids;
        } catch (Exception e) {
            return null;
        }
    }

    public record AdminSseConnection(Long userId, SseEmitter emitter, long connectedAt) {
        AdminSseConnection(Long userId, SseEmitter emitter) {
            this(userId, emitter, System.currentTimeMillis());
        }
    }
}
