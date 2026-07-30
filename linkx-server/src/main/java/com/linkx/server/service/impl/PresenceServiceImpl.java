package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.PresenceService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    public static final String CONN_KEY_PREFIX = "linkx:presence:conn:";
    public static final String EVENTS_CHANNEL = "linkx:presence:events";
    public static final String INSTANCES_KEY = "linkx:presence:instances";
    public static final String HB_KEY_PREFIX = "linkx:presence:hb:";
    public static final String BY_INST_KEY_PREFIX = "linkx:presence:byinst:";

    private static final String INSTANCE_ID = UUID.randomUUID().toString().replace("-", "");
    private static final Duration INSTANCE_HB_TTL = Duration.ofSeconds(15);

    /** SCARD + SADD + EXPIRE 原子化，返回 [before, after] */
    private static final String MARK_ONLINE_LUA =
            "local before = redis.call('SCARD', KEYS[1])\n" +
            "redis.call('SADD', KEYS[1], ARGV[1])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
            "local after = redis.call('SCARD', KEYS[1])\n" +
            "return {before, after}";

    /** SREM + SCARD(+DEL) 原子化，返回 after */
    private static final String MARK_OFFLINE_LUA =
            "redis.call('SREM', KEYS[1], ARGV[1])\n" +
            "local after = redis.call('SCARD', KEYS[1])\n" +
            "if after == 0 then redis.call('DEL', KEYS[1]) end\n" +
            "return after";

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String getInstanceId() {
        return INSTANCE_ID;
    }

    @Override
    public void markOnline(Long userId, String deviceId, String connId) {
        if (userId == null) {
            return;
        }
        String key = connKey(userId);
        String member = member(deviceId, connId);
        long ttlSeconds = Math.max(ttl().getSeconds(), 30L);
        @SuppressWarnings("unchecked")
        java.util.List<Long> counts = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(MARK_ONLINE_LUA, java.util.List.class),
                java.util.List.of(key),
                member,
                String.valueOf(ttlSeconds)
        );

        boolean tracked = false;
        try {
            trackInstanceMember(userId, member);
            tracked = true;
            refreshInstanceHeartbeat();
        } catch (Exception e) {
            log.warn("presence 实例追踪更新失败: userId={}, instance={}, err={}", userId, INSTANCE_ID, e.getMessage());
        }
        if (!tracked) {
            redisTemplate.opsForSet().remove(key, member);
            Long after = redisTemplate.opsForSet().size(key);
            if (after == null || after == 0L) {
                redisTemplate.delete(key);
            }
            return;
        }

        long before = counts != null && !counts.isEmpty() && counts.get(0) != null ? counts.get(0) : 0L;
        long after = counts != null && counts.size() > 1 && counts.get(1) != null ? counts.get(1) : 0L;
        boolean becameOnline = before == 0L && after > 0L;
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
        Long after = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(MARK_OFFLINE_LUA, Long.class),
                java.util.List.of(key),
                member
        );
        try {
            untrackInstanceMember(userId, member);
        } catch (Exception e) {
            log.warn("presence 实例追踪移除失败: userId={}, instance={}, err={}", userId, INSTANCE_ID, e.getMessage());
        }
        if (after == null || after == 0L) {
            publish(userId, false);
            log.debug("presence offline: userId={}, instance={}", userId, INSTANCE_ID);
        } else {
            refreshTtl(key);
        }
    }

    /**
     * 原子刷新用户在线 TTL：仅当连接集合存在时才续期，避免 hasKey + expire 的竞态窗口。
     */
    private static final String TOUCH_LUA =
            "if redis.call('EXISTS', KEYS[1]) == 1 then\n" +
            "  return redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return 0";

    @Override
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }
        String key = connKey(userId);
        redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(TOUCH_LUA, Long.class),
                java.util.List.of(key),
                String.valueOf(ttl().getSeconds())
        );
        refreshInstanceHeartbeat();
    }

    @Override
    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        Long size = redisTemplate.opsForSet().size(connKey(userId));
        return size != null && size > 0L;
    }

    @Override
    public void broadcastPresence(Long userId, boolean online) {
        if (userId == null) {
            return;
        }
        publish(userId, online);
    }

    @Override
    @PreDestroy
    public void clearLocalPresenceOnShutdown() {
        try {
            String byInstKey = BY_INST_KEY_PREFIX + INSTANCE_ID;
            Set<String> entries = redisTemplate.opsForSet().members(byInstKey);
            if (entries != null) {
                for (String entry : entries) {
                    reclaimEntry(entry);
                }
            }
            redisTemplate.delete(byInstKey);
            redisTemplate.delete(HB_KEY_PREFIX + INSTANCE_ID);
            redisTemplate.opsForSet().remove(INSTANCES_KEY, INSTANCE_ID);
            log.info("presence 优雅停机清理完成: instance={}", INSTANCE_ID);
        } catch (Exception e) {
            log.warn("presence 停机清理失败: {}", e.getMessage());
        }
    }

    @Override
    public void refreshInstanceHeartbeat() {
        try {
            redisTemplate.opsForValue().set(HB_KEY_PREFIX + INSTANCE_ID, "1", INSTANCE_HB_TTL);
            redisTemplate.opsForSet().add(INSTANCES_KEY, INSTANCE_ID);
        } catch (Exception e) {
            log.debug("刷新 presence 实例心跳失败: {}", e.getMessage());
        }
    }

    @Override
    public void sweepDeadInstances() {
        try {
            Set<String> instances = redisTemplate.opsForSet().members(INSTANCES_KEY);
            if (instances == null || instances.isEmpty()) {
                return;
            }
            for (String instanceId : instances) {
                if (INSTANCE_ID.equals(instanceId)) {
                    continue;
                }
                Boolean alive = redisTemplate.hasKey(HB_KEY_PREFIX + instanceId);
                if (Boolean.TRUE.equals(alive)) {
                    continue;
                }
                log.info("清扫宕机 presence 实例: {}", instanceId);
                String byInstKey = BY_INST_KEY_PREFIX + instanceId;
                Set<String> entries = redisTemplate.opsForSet().members(byInstKey);
                if (entries != null) {
                    for (String entry : entries) {
                        reclaimEntry(entry);
                    }
                }
                redisTemplate.delete(byInstKey);
                redisTemplate.opsForSet().remove(INSTANCES_KEY, instanceId);
            }
        } catch (Exception e) {
            log.warn("清扫宕机 presence 实例失败: {}", e.getMessage());
        }
    }

    private void reclaimEntry(String entry) {
        // 格式: userId\tmember
        if (entry == null || entry.isBlank()) {
            return;
        }
        int sep = entry.indexOf('\t');
        if (sep <= 0 || sep >= entry.length() - 1) {
            return;
        }
        Long userId;
        try {
            userId = Long.parseLong(entry.substring(0, sep));
        } catch (NumberFormatException e) {
            return;
        }
        String member = entry.substring(sep + 1);
        String key = connKey(userId);
        redisTemplate.opsForSet().remove(key, member);
        Long after = redisTemplate.opsForSet().size(key);
        if (after == null || after == 0L) {
            redisTemplate.delete(key);
            publish(userId, false);
        }
    }

    private void trackInstanceMember(Long userId, String member) {
        String byInstKey = BY_INST_KEY_PREFIX + INSTANCE_ID;
        redisTemplate.opsForSet().add(byInstKey, userId + "\t" + member);
        redisTemplate.expire(byInstKey, Duration.ofHours(24));
    }

    private void untrackInstanceMember(Long userId, String member) {
        redisTemplate.opsForSet().remove(BY_INST_KEY_PREFIX + INSTANCE_ID, userId + "\t" + member);
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
        // 兜底 TTL：心跳的 2 倍，至少 45s；宕机清扫负责秒级收敛
        return Duration.ofSeconds(Math.max(45L, heartbeat * 2L));
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
