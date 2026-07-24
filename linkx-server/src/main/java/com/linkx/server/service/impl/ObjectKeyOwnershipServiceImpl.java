package com.linkx.server.service.impl;

import com.linkx.server.exception.CustomException;
import com.linkx.server.service.ObjectKeyOwnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ObjectKeyOwnershipServiceImpl implements ObjectKeyOwnershipService {

    private static final String KEY_PREFIX = "linkx:obj-owner:";
    /** 与业务文件长期存活对齐；删除对象时可不删登记（失效 key 无法打开） */
    private static final Duration TTL = Duration.ofDays(400);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void claim(Long userId, String objectKey) {
        if (userId == null || !StringUtils.hasText(objectKey)) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + normalize(objectKey), String.valueOf(userId), TTL);
    }

    @Override
    public boolean isOwned(Long userId, String objectKey) {
        if (userId == null || !StringUtils.hasText(objectKey)) {
            return false;
        }
        String owner = redisTemplate.opsForValue().get(KEY_PREFIX + normalize(objectKey));
        return String.valueOf(userId).equals(owner);
    }

    @Override
    public void assertOwned(Long userId, String objectKey) {
        if (!isOwned(userId, objectKey)) {
            throw new CustomException(403, "无权访问该文件");
        }
    }

    private static String normalize(String objectKey) {
        String key = objectKey.trim();
        if (key.startsWith("lx-media:")) {
            key = key.substring("lx-media:".length());
        }
        return key;
    }
}
