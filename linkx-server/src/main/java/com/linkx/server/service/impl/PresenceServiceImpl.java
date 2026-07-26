package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    public static final String CONN_KEY_PREFIX = "linkx:presence:conn:";
    public static final String EVENTS_CHANNEL = "linkx:presence:events";

    private static final String INSTANCE_ID = UUID.randomUUID().toString().replace("-", "");

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void markOnline(Long userId, String deviceId, String connId) {
        if (userId == null) {
            return;
        }
        String key = connKey(userId);
        String member = member(deviceId, connId);
        Long before = redisTemplate.opsForSet().size(key);
        redisTemplate.opsForSet().add(key, member);
        refreshTtl(key);
        Long after = redisTemplate.opsForSet().size(key);
        boolean becameOnline = (before == null || before == 0L) && after != null && after > 0L;
        if (becameOnline) {
            publish(userId, true);
            log.debug("presence online: userId={}, instance={}", userId, INSTANCE_ID);
        }
    }

    @Override
    public void markOffline(Long userId, String deviceId, String connId) {
        if (userId == null) {
            return;
        }
        String key = connKey(userId);
        String member = member(deviceId, connId);
        redisTemplate.opsForSet().remove(key, member);
        Long after = redisTemplate.opsForSet().size(key);
        if (after == null || after == 0L) {
            redisTemplate.delete(key);
            publish(userId, false);
            log.debug("presence offline: userId={}, instance={}", userId, INSTANCE_ID);
        } else {
            refreshTtl(key);
        }
    }

    @Override
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }
        String key = connKey(userId);
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            refreshTtl(key);
        }
    }

    @Override
    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        Long size = redisTemplate.opsForSet().size(connKey(userId));
        return size != null && size > 0L;
    }

    private void publish(Long userId, boolean online) {
        try {
            Map<String, Object> payload = new HashMap<>(4);
            payload.put("userId", userId);
            payload.put("online", online);
            payload.put("ts", System.currentTimeMillis());
            redisTemplate.convertAndSend(EVENTS_CHANNEL, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("发布 presence 事件失败: userId={}, online={}, err={}", userId, online, e.getMessage());
        }
    }

    private void refreshTtl(String key) {
        redisTemplate.expire(key, ttl());
    }

    private Duration ttl() {
        int heartbeat = linkxProperties.getIm().getHeartbeatIntervalSeconds();
        if (heartbeat <= 0) {
            heartbeat = 30;
        }
        return Duration.ofSeconds(Math.max(90L, heartbeat * 3L));
    }

    private static String connKey(Long userId) {
        return CONN_KEY_PREFIX + userId;
    }

    private static String member(String deviceId, String connId) {
        String device = (deviceId == null || deviceId.isBlank()) ? "default" : deviceId.trim();
        String conn = (connId == null || connId.isBlank()) ? UUID.randomUUID().toString() : connId.trim();
        return INSTANCE_ID + ":" + device + ":" + conn;
    }
}
