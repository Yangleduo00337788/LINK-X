package com.linkx.server.service.impl;

import com.linkx.server.entity.ImMessageStormEvent;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImMessageStormEventMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.MessageStormService;
import com.linkx.server.service.admin.AdminRiskEventService;
import com.linkx.server.config.LinkxProperties;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStormServiceImpl implements MessageStormService {

    private static final String USER_STORM_PREFIX = "linkx:msg:storm:";

    private static final String GROUP_STORM_PREFIX = "linkx:storm:";

    /**
     * 原子 INCR + EXPIRE Lua 脚本：
     * - 第一次 INCR 时同时设置 TTL，避免非原子操作导致键永不过期；
     * - 返回当前计数。
     */
    private static final String STORM_INCR_LUA =
            "local n = redis.call('INCR', KEYS[1])\n" +
            "if n == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return n";

    private final StringRedisTemplate redisTemplate;
    private final ImMessageStormEventMapper stormEventMapper;
    private final AuditLogService auditLogService;
    private final AdminRiskEventService adminRiskEventService;
    private final LinkxProperties linkxProperties;

    private int userStormThreshold() {
        return linkxProperties.getRiskPolicy().getMessageStormUserThreshold();
    }

    private int userStormWindowSeconds() {
        return linkxProperties.getRiskPolicy().getMessageStormUserWindowSeconds();
    }

    @Override
    public boolean checkAndRecordUserStorm(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = USER_STORM_PREFIX + userId;
        // 使用 Lua 脚本原子完成 INCR + EXPIRE，避免非原子操作导致键永不过期
        // 键永不过期会让用户被永久限流（不可接受的故障）
        Long count = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                        STORM_INCR_LUA,
                        Long.class
                ),
                List.of(key),
                String.valueOf(userStormWindowSeconds())
        );
        int threshold = userStormThreshold();
        if (count != null && count > threshold) {
            // 同一窗口内只落库一次，避免刷爆
            if (count == threshold + 1L) {
                persist(userId, null, ImMessageStormEvent.TYPE_USER_RATE,
                        count.intValue(), userStormWindowSeconds(), null);
            }
            log.warn("消息风暴检测: userId={}, count={}", userId, count);
            return true;
        }
        return false;
    }

    @Override
    public void checkAndRecordGroupStorm(Long userId, Long conversationId, int memberCount) {
        LinkxProperties.RiskPolicy policy = linkxProperties.getRiskPolicy();
        if (memberCount < policy.getMessageStormGroupMinMembers()) {
            return;
        }
        int maxPerMinute = memberCount >= policy.getMessageStormGroupLargeMemberThreshold()
                ? policy.getMessageStormGroupLargeMaxPerMinute()
                : policy.getMessageStormGroupMidMaxPerMinute();
        String userStormKey = GROUP_STORM_PREFIX + conversationId + ":user:" + userId;
        Long count = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                        STORM_INCR_LUA,
                        Long.class
                ),
                List.of(userStormKey),
                "60"
        );
        if (count != null && count > maxPerMinute) {
            if (count == maxPerMinute + 1L) {
                persist(userId, conversationId, ImMessageStormEvent.TYPE_GROUP_RATE,
                        count.intValue(), 60, memberCount);
            }
            throw new CustomException(429,
                    "群消息发送过于频繁，请稍后再试（" + memberCount + "人以上大群限制每分钟" + maxPerMinute + "条）");
        }
    }

    @Override
    public long countRecentEvents(Long userId) {
        if (userId == null) {
            return 0;
        }
        return stormEventMapper.selectCountByQuery(
                QueryWrapper.create().where(ImMessageStormEvent::getUserId).eq(userId));
    }

    private void persist(Long userId, Long conversationId, String eventType,
                         int messageCount, int windowSeconds, Integer memberCount) {
        try {
            ImMessageStormEvent event = ImMessageStormEvent.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .eventType(eventType)
                    .messageCount(messageCount)
                    .windowSeconds(windowSeconds)
                    .memberCount(memberCount)
                    .createTime(new Date())
                    .build();
            stormEventMapper.insert(event);
            auditLogService.log(
                    SysAuditLog.OperationType.MESSAGE_STORM,
                    "消息风暴: " + eventType + " count=" + messageCount,
                    userId,
                    null,
                    null,
                    null,
                    true,
                    null
            );
            adminRiskEventService.recordMessageStorm(userId, eventType, messageCount, conversationId);
        } catch (Exception e) {
            log.warn("风暴事件落库失败: userId={}, type={}", userId, eventType, e);
        }
    }
}
