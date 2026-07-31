package com.linkx.server.service.admin;

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
import java.util.List;
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
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
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
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // ignore
            }
        }
        emitters.clear();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"ok\":true}"));
        } catch (IOException e) {
            emitters.remove(emitter);
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
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("admin_event").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }
}
