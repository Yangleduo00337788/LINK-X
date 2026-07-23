package com.linkx.server.service.impl;

import com.linkx.server.service.ConversationDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ConversationDraftServiceImpl implements ConversationDraftService {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveDraft(Long userId, Long conversationId, String content) {
        String key = key(userId, conversationId);
        if (!StringUtils.hasText(content)) {
            redisTemplate.delete(key);
            return;
        }
        redisTemplate.opsForValue().set(key, content, TTL);
    }

    @Override
    public String getDraft(Long userId, Long conversationId) {
        String value = redisTemplate.opsForValue().get(key(userId, conversationId));
        return value != null ? value : "";
    }

    @Override
    public void clearDraft(Long userId, Long conversationId) {
        redisTemplate.delete(key(userId, conversationId));
    }

    private static String key(Long userId, Long conversationId) {
        return "linkx:draft:" + userId + ":" + conversationId;
    }
}
