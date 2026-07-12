package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private static final String RATE_LIMIT_PREFIX = "linkx:rate:";
    private static final String LOGIN_FAIL_PREFIX = "linkx:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "linkx:login:lock:";
    private static final String IP_PREFIX = "ip:";

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;

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

    @Override
    public void checkLoginRateLimit(String username, HttpServletRequest request) {
        String ip = getClientIp(request);
        int maxAttempts = linkxProperties.getAuth().getLoginMaxAttempts();
        int lockDuration = linkxProperties.getAuth().getLockDurationMinutes();
        int ipMaxAttempts = maxAttempts * 3; // IP 限制更宽松

        // 检查 IP 级别限流
        String ipKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + IP_PREFIX + ip;
        Long ipCount = redisTemplate.opsForValue().increment(ipKey);
        if (ipCount != null && ipCount == 1L) {
            redisTemplate.expire(ipKey, Duration.ofMinutes(lockDuration));
        }
        if (ipCount != null && ipCount > ipMaxAttempts) {
            throw new CustomException(429, "该IP登录尝试过多，请" + lockDuration + "分钟后重试");
        }

        // 检查用户名级别限流
        String userKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + username;
        Long userCount = redisTemplate.opsForValue().increment(userKey);
        if (userCount != null && userCount == 1L) {
            redisTemplate.expire(userKey, Duration.ofMinutes(lockDuration));
        }
        if (userCount != null && userCount > maxAttempts) {
            // 设置账号锁定
            String lockKey = LOGIN_LOCK_PREFIX + username;
            redisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(lockDuration));
            throw new CustomException(429, "登录失败次数过多，请" + lockDuration + "分钟后重试");
        }
    }

    @Override
    public void checkRegisterRateLimit(HttpServletRequest request) {
        String ip = getClientIp(request);
        int maxAttempts = linkxProperties.getAuth().getRateLimitRegisterPerMinute();
        int windowSeconds = 60;

        String ipKey = RATE_LIMIT_PREFIX + "register:" + IP_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(ipKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(ipKey, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > maxAttempts) {
            throw new CustomException(429, "注册过于频繁，请稍后再试");
        }
    }

    @Override
    public int recordLoginFailure(String username, HttpServletRequest request) {
        // 已在 checkLoginRateLimit 中记录，此方法用于兼容旧接口
        String userKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + username;
        String count = redisTemplate.opsForValue().get(userKey);
        return count != null ? Integer.parseInt(count) : 0;
    }

    @Override
    public boolean isAccountLocked(String username, HttpServletRequest request) {
        String lockKey = LOGIN_LOCK_PREFIX + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    @Override
    public void clearLoginFailure(String username, HttpServletRequest request) {
        String userKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + username;
        String lockKey = LOGIN_LOCK_PREFIX + username;
        redisTemplate.delete(userKey);
        redisTemplate.delete(lockKey);

        // 也清除 IP 级别的记录（可选，保留可防范同一IP切换账号攻击）
        // String ip = getClientIp(request);
        // String ipKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + IP_PREFIX + ip;
        // redisTemplate.delete(ipKey);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个IP时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
