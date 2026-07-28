package com.linkx.server.im;

import com.linkx.server.service.PresenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 跨实例 IM 帧投递消费者（Redis Stream）。
 * <p>
 * 每实例维护独立游标：短暂宕机后可从断点续读，避免 Pub/Sub 订阅离线即永久丢帧。
 * 本机发布的帧仍跳过（发布侧已 deliverLocal）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImClusterPushSubscriber {

    private static final String CURSOR_KEY_PREFIX = "linkx:im:push:cursor:";

    private final PresenceService presenceService;
    private final ImMessagePushService pushService;
    private final StringRedisTemplate redisTemplate;

    private volatile boolean running;
    private Thread worker;

    @PostConstruct
    public void start() {
        running = true;
        worker = new Thread(this::consumeLoop, "im-cluster-push-stream");
        worker.setDaemon(true);
        worker.start();
        log.info("IM 跨实例 Stream 消费者已启动: stream={}", ImMessagePushService.CLUSTER_PUSH_STREAM);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void consumeLoop() {
        String cursorKey = CURSOR_KEY_PREFIX + presenceService.getInstanceId();
        String lastId = redisTemplate.opsForValue().get(cursorKey);
        if (lastId == null || lastId.isBlank()) {
            // 新实例只收启动后的新帧，避免回放历史 IM 推送
            lastId = "$";
        }
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        StreamReadOptions.empty().count(50).block(Duration.ofSeconds(1)),
                        StreamOffset.create(ImMessagePushService.CLUSTER_PUSH_STREAM, ReadOffset.from(lastId))
                );
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    lastId = record.getId().getValue();
                    handleRecord(record.getValue());
                }
                redisTemplate.opsForValue().set(cursorKey, lastId, Duration.ofDays(7));
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.warn("处理跨实例 IM Stream 失败: {}", e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleRecord(Map<Object, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        String origin = stringField(fields, "origin");
        if (presenceService.getInstanceId().equals(origin)) {
            return;
        }
        Long userId = parseUserId(stringField(fields, "userId"));
        String frame = stringField(fields, "frame");
        if (userId == null || frame == null || frame.isBlank()) {
            return;
        }
        pushService.deliverLocal(userId, frame);
    }

    private static String stringField(Map<Object, Object> fields, String key) {
        Object val = fields.get(key);
        return val == null ? null : String.valueOf(val);
    }

    private static Long parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
