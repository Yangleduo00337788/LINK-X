package com.linkx.server.service.admin.impl;

import com.linkx.server.service.admin.ApiSignNonceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ApiSignNonceServiceImpl implements ApiSignNonceService {

    private static final String KEY_PREFIX = "linkx:admin:sign-nonce:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean registerNonce(String nonce, Duration ttl) {
        if (!StringUtils.hasText(nonce)) {
            return false;
        }
        Duration effective = ttl == null ? Duration.ofMinutes(3) : ttl;
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + nonce.trim(),
                "1",
                effective);
        return Boolean.TRUE.equals(ok);
    }
}
