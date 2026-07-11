package com.linkx.server.service.impl;

import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private static final String RATE_LIMIT_PREFIX = "linkx:rate:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void check(String key, int maxAttempts, int windowSeconds) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > maxAttempts) {
            throw new CustomException(429, "操作过于频繁，请稍后再试");
        }
    }
}
