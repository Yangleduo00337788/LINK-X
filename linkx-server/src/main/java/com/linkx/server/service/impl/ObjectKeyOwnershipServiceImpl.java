package com.linkx.server.service.impl;

import com.linkx.server.entity.SysObjectOwnership;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysObjectOwnershipMapper;
import com.linkx.server.service.ObjectKeyOwnershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;

/**
 * 属主登记：MySQL 为权威源，Redis 为热缓存；cache miss / flush 后从 DB 回填。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectKeyOwnershipServiceImpl implements ObjectKeyOwnershipService {

    private static final String KEY_PREFIX = "linkx:obj-owner:";
    /** 与业务文件长期存活对齐；删除对象时可不删登记（失效 key 无法打开） */
    private static final Duration TTL = Duration.ofDays(400);

    private final StringRedisTemplate redisTemplate;
    private final SysObjectOwnershipMapper ownershipMapper;

    @Override
    public void claim(Long userId, String objectKey) {
        if (userId == null || !StringUtils.hasText(objectKey)) {
            return;
        }
        String key = normalize(objectKey);
        Date now = new Date();
        SysObjectOwnership row = SysObjectOwnership.builder()
                .objectKey(key)
                .userId(userId)
                .createTime(now)
                .updateTime(now)
                .build();
        // insertOrUpdate：覆盖属主（与原 Redis set 语义一致）
        ownershipMapper.insertOrUpdate(row);
        redisTemplate.opsForValue().set(KEY_PREFIX + key, String.valueOf(userId), TTL);
    }

    @Override
    public boolean isOwned(Long userId, String objectKey) {
        if (userId == null || !StringUtils.hasText(objectKey)) {
            return false;
        }
        String key = normalize(objectKey);
        String cached = redisTemplate.opsForValue().get(KEY_PREFIX + key);
        if (StringUtils.hasText(cached)) {
            return String.valueOf(userId).equals(cached);
        }
        SysObjectOwnership row = ownershipMapper.selectOneById(key);
        if (row == null || row.getUserId() == null) {
            return false;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + key, String.valueOf(row.getUserId()), TTL);
        return userId.equals(row.getUserId());
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
        int q = key.indexOf('?');
        if (q >= 0) {
            key = key.substring(0, q);
        }
        return key;
    }
}
