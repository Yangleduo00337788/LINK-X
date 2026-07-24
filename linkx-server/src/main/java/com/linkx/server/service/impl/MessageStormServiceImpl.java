package com.linkx.server.service.impl;

import com.linkx.server.entity.ImMessageStormEvent;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImMessageStormEventMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.MessageStormService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStormServiceImpl implements MessageStormService {

    private static final String USER_STORM_PREFIX = "linkx:msg:storm:";
    private static final int USER_STORM_THRESHOLD = 30;
    private static final int USER_STORM_WINDOW_SECONDS = 10;

    private static final String GROUP_STORM_PREFIX = "linkx:storm:";

    private final StringRedisTemplate redisTemplate;
    private final ImMessageStormEventMapper stormEventMapper;
    private final AuditLogService auditLogService;

    @Override
    public boolean checkAndRecordUserStorm(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = USER_STORM_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(USER_STORM_WINDOW_SECONDS));
        }
        if (count != null && count > USER_STORM_THRESHOLD) {
            // 同一窗口内只落库一次，避免刷爆
            if (count == USER_STORM_THRESHOLD + 1L) {
                persist(userId, null, ImMessageStormEvent.TYPE_USER_RATE,
                        count.intValue(), USER_STORM_WINDOW_SECONDS, null);
            }
            log.warn("消息风暴检测: userId={}, count={}", userId, count);
            return true;
        }
        return false;
    }

    @Override
    public void checkAndRecordGroupStorm(Long userId, Long conversationId, int memberCount) {
        if (memberCount < 500) {
            return;
        }
        int maxPerMinute = memberCount >= 1000 ? 5 : 10;
        String userStormKey = GROUP_STORM_PREFIX + conversationId + ":user:" + userId;
        Long count = redisTemplate.opsForValue().increment(userStormKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(userStormKey, Duration.ofMinutes(1));
        }
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
        } catch (Exception e) {
            log.warn("风暴事件落库失败: userId={}, type={}", userId, eventType, e);
        }
    }
}
