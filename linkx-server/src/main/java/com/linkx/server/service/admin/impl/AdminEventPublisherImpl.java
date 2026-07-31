package com.linkx.server.service.admin.impl;

import com.linkx.server.service.admin.AdminEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEventPublisherImpl implements AdminEventPublisher {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void publish(String type, Long relatedId) {
        publish(type, relatedId, null);
    }

    @Override
    public void publish(String type, Long relatedId, String extraJson) {
        if (!StringUtils.hasText(type)) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"type\":\"").append(escape(type)).append('"');
            sb.append(",\"relatedId\":\"").append(relatedId != null ? relatedId : "").append('"');
            sb.append(",\"ts\":").append(System.currentTimeMillis());
            if (StringUtils.hasText(extraJson) && extraJson.trim().startsWith("{")) {
                String trimmed = extraJson.trim();
                // 合并额外字段：去掉外层 {}
                String inner = trimmed.substring(1, trimmed.length() - 1).trim();
                if (!inner.isEmpty()) {
                    sb.append(',').append(inner);
                }
            }
            sb.append('}');
            redisTemplate.convertAndSend(CHANNEL, sb.toString());
        } catch (Exception e) {
            log.debug("发布管理端事件失败: type={}, err={}", type, e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
