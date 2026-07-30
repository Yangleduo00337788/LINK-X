package com.linkx.server.service.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.LoginSide;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private static final String RATE_LIMIT_PREFIX = "linkx:rate:";
    private static final String LOGIN_FAIL_PREFIX = "linkx:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "linkx:login:lock:";
    private static final String REFRESH_FAIL_PREFIX = "linkx:refresh:fail:";
    private static final String IP_PREFIX = "ip:";

    private static final int REFRESH_FAIL_THRESHOLD = 10;
    private static final int REFRESH_FAIL_WINDOW_MINUTES = 15;

    /** Lua 脚本：原子化 INCR + 首次设置 EXPIRE，避免 increment 与 expire 分两步导致计数无过期 */
    private static final String INCR_EXPIRE_LUA =
            "local c=redis.call('INCR',KEYS[1]) if c==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end return c";

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;

    @Override
    public void check(String key, int maxAttempts, int windowSeconds) {
        check(key, maxAttempts, windowSeconds, "操作过于频繁，请稍后再试");
    }

    @Override
    public void check(String key, int maxAttempts, int windowSeconds, String message) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        Long count = atomicIncrAndExpire(redisKey, windowSeconds);
        if (count != null && count > maxAttempts) {
            throw new CustomException(429, message);
        }
    }

    @Override
    public boolean checkLoginRateLimit(String username, HttpServletRequest request, LoginSide side) {
        String ip = getClientIp(request);
        int maxAttempts = resolveMaxAttempts(side);
        int lockDuration = resolveLockDuration(side);
        int ipMaxAttempts = maxAttempts * 3; // IP 限制更宽松
        String sideKey = side.name().toLowerCase();

        // 检查 IP 级别限流
        String ipKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + sideKey + ":" + IP_PREFIX + ip;
        Long ipCount = atomicIncrAndExpire(ipKey, lockDuration * 60L);
        if (ipCount != null && ipCount >= ipMaxAttempts) {
            throw new CustomException(429, "该IP登录尝试过多，请" + lockDuration + "分钟后重试");
        }

        // 检查用户名级别限流（达到 maxAttempts 次即锁定，含本次数）
        String userKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + sideKey + ":" + username;
        Long userCount = atomicIncrAndExpire(userKey, lockDuration * 60L);
        if (userCount != null && userCount >= maxAttempts) {
            String lockKey = LOGIN_LOCK_PREFIX + sideKey + ":" + username;
            redisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(lockDuration));
            return true;
        }
        return false;
    }

    @Override
    public void checkRegisterRateLimit(HttpServletRequest request) {
        String ip = getClientIp(request);
        int maxAttempts = linkxProperties.getAuth().getRateLimitRegisterPerMinute();
        int windowSeconds = 60;

        String ipKey = RATE_LIMIT_PREFIX + "register:" + IP_PREFIX + ip;
        Long count = atomicIncrAndExpire(ipKey, windowSeconds);
        if (count != null && count > maxAttempts) {
            throw new CustomException(429, "注册过于频繁，请稍后再试");
        }
    }

    @Override
    public int recordLoginFailure(String username, HttpServletRequest request, LoginSide side) {
        String sideKey = side.name().toLowerCase();
        String userKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + sideKey + ":" + username;
        String count = redisTemplate.opsForValue().get(userKey);
        return count != null ? Integer.parseInt(count) : 0;
    }

    @Override
    public boolean isAccountLocked(String username, LoginSide side) {
        String lockKey = LOGIN_LOCK_PREFIX + side.name().toLowerCase() + ":" + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    @Override
    public void clearLoginFailure(String username, LoginSide side) {
        String sideKey = side.name().toLowerCase();
        String userKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + sideKey + ":" + username;
        String lockKey = LOGIN_LOCK_PREFIX + sideKey + ":" + username;
        redisTemplate.delete(userKey);
        redisTemplate.delete(lockKey);
    }

    private int resolveMaxAttempts(LoginSide side) {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        return side == LoginSide.ADMIN ? auth.getAdminLoginMaxAttempts() : auth.getLoginMaxAttempts();
    }

    private int resolveLockDuration(LoginSide side) {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        return side == LoginSide.ADMIN ? auth.getAdminLockDurationMinutes() : auth.getLockDurationMinutes();
    }

    /**
     * 原子化自增并设置过期：仅首次自增（c==1）时设置 EXPIRE，避免计数键永不过期。
     */
    private Long atomicIncrAndExpire(String key, long windowSeconds) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_EXPIRE_LUA, Long.class);
        return redisTemplate.execute(script, List.of(key), String.valueOf(windowSeconds));
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, linkxProperties);
    }

    @Override
    public void recordRefreshFailure(HttpServletRequest request) {
        String ip = getClientIp(request);
        String key = RATE_LIMIT_PREFIX + REFRESH_FAIL_PREFIX + IP_PREFIX + ip;
        Long count = atomicIncrAndExpire(key, REFRESH_FAIL_WINDOW_MINUTES * 60L);
        if (count != null && count >= REFRESH_FAIL_THRESHOLD) {
            throw new CustomException(429,
                    "refresh token 失败次数过多，请" + REFRESH_FAIL_WINDOW_MINUTES + "分钟后再试");
        }
    }
}
