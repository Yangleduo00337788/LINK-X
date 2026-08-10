package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.LoginSide;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.vo.AdminRateLimitHitVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.service.admin.AdminRiskEventService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private static final String RATE_LIMIT_PREFIX = "linkx:rate:";
    private static final String LOGIN_FAIL_PREFIX = "linkx:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "linkx:login:lock:";
    private static final String REFRESH_FAIL_PREFIX = "linkx:refresh:fail:";
    private static final String IP_PREFIX = "ip:";
    private static final String WHITELIST_KEY = "linkx:rate:whitelist";

    private static final int REFRESH_FAIL_THRESHOLD = 10;
    private static final int REFRESH_FAIL_WINDOW_MINUTES = 15;

    /** Lua 脚本：原子化 INCR + 首次设置 EXPIRE，避免 increment 与 expire 分两步导致计数无过期 */
    private static final String INCR_EXPIRE_LUA =
            "local c=redis.call('INCR',KEYS[1]) if c==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end return c";

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;
    private final AdminRiskEventService adminRiskEventService;

    @Override
    public void check(String key, int maxAttempts, int windowSeconds) {
        check(key, maxAttempts, windowSeconds, "操作过于频繁，请稍后再试");
    }

    @Override
    public void check(String key, int maxAttempts, int windowSeconds, String message) {
        if (key != null && key.contains("ip:") && isWhitelisted(extractIpFromKey(key))) {
            return;
        }
        String redisKey = RATE_LIMIT_PREFIX + key;
        Long count = atomicIncrAndExpire(redisKey, windowSeconds);
        if (count != null && count > maxAttempts) {
            // 仅首次超限落库，避免同一窗口重复刷风险事件
            if (count == maxAttempts + 1L) {
                recordBizRateLimit(key);
            }
            throw new CustomException(429, message);
        }
    }

    @Override
    public boolean checkLoginRateLimit(String username, HttpServletRequest request, LoginSide side) {
        String ip = getClientIp(request);
        if (isWhitelisted(ip)) {
            return false;
        }
        int maxAttempts = resolveMaxAttempts(side);
        int lockDuration = resolveLockDuration(side);
        int ipMaxAttempts = maxAttempts * 3; // IP 限制更宽松
        String sideKey = side.name().toLowerCase();

        // 检查 IP 级别限流
        String ipKey = RATE_LIMIT_PREFIX + LOGIN_FAIL_PREFIX + sideKey + ":" + IP_PREFIX + ip;
        Long ipCount = atomicIncrAndExpire(ipKey, lockDuration * 60L);
        if (ipCount != null && ipCount >= ipMaxAttempts) {
            if (ipCount == (long) ipMaxAttempts) {
                try {
                    adminRiskEventService.recordRateLimit(null, "ip:" + ip, "login-ip:" + sideKey, ip);
                } catch (Exception e) {
                    log.warn("登录 IP 限流风险事件写入失败: ip={}, side={}", ip, sideKey, e);
                }
            }
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
        if (isWhitelisted(ip)) {
            return;
        }
        int maxAttempts = linkxProperties.getAuth().getRateLimitRegisterPerMinute();
        int windowSeconds = 60;

        String ipKey = RATE_LIMIT_PREFIX + "register:" + IP_PREFIX + ip;
        Long count = atomicIncrAndExpire(ipKey, windowSeconds);
        if (count != null && count > maxAttempts) {
            throw new CustomException(429, "注册过于频繁，请稍后再试");
        }
    }

    @Override
    public void checkLoginRequestRateLimit(HttpServletRequest request, LoginSide side) {
        String ip = getClientIp(request);
        if (isWhitelisted(ip)) {
            return;
        }
        LoginSide loginSide = side == null ? LoginSide.CLIENT : side;
        int maxAttempts = linkxProperties.getAuth().getRateLimitLoginPerMinute();
        String sideKey = loginSide.name().toLowerCase();
        String ipKey = RATE_LIMIT_PREFIX + "login-request:" + sideKey + ":" + IP_PREFIX + ip;
        Long count = atomicIncrAndExpire(ipKey, 60L);
        if (count != null && count > maxAttempts) {
            if (count == maxAttempts + 1L) {
                try {
                    adminRiskEventService.recordRateLimit(null, "ip:" + ip, "login-request:" + sideKey, ip);
                } catch (Exception e) {
                    log.warn("登录请求限流风险事件写入失败: ip={}, side={}", ip, sideKey, e);
                }
            }
            throw new CustomException(429, "登录请求过于频繁，请稍后再试");
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
        if (isWhitelisted(ip)) {
            return;
        }
        String key = RATE_LIMIT_PREFIX + REFRESH_FAIL_PREFIX + IP_PREFIX + ip;
        Long count = atomicIncrAndExpire(key, REFRESH_FAIL_WINDOW_MINUTES * 60L);
        if (count != null && count >= REFRESH_FAIL_THRESHOLD) {
            if (count == (long) REFRESH_FAIL_THRESHOLD) {
                try {
                    adminRiskEventService.recordRateLimit(null, "ip:" + ip, "refresh-token", ip);
                } catch (Exception e) {
                    log.warn("refresh 限流风险事件写入失败: ip={}", ip, e);
                }
            }
            throw new CustomException(429,
                    "refresh token 失败次数过多，请" + REFRESH_FAIL_WINDOW_MINUTES + "分钟后再试");
        }
    }

    private void recordBizRateLimit(String key) {
        try {
            // key 形如 biz:{scope}:{identity}
            String raw = key == null ? "" : key;
            String scope = "unknown";
            String identity = raw;
            Long userId = null;
            String ip = null;
            if (raw.startsWith("biz:")) {
                String rest = raw.substring(4);
                int idx = rest.indexOf(':');
                if (idx > 0) {
                    scope = rest.substring(0, idx);
                    identity = rest.substring(idx + 1);
                } else {
                    scope = rest;
                }
            }
            if (identity.startsWith("ip:")) {
                ip = identity.substring(3);
            } else {
                try {
                    userId = Long.parseLong(identity);
                } catch (NumberFormatException ignored) {
                    // identity 非数字时仅作为字符串记录
                }
            }
            adminRiskEventService.recordRateLimit(userId, identity, scope, ip);
        } catch (Exception e) {
            log.warn("业务限流风险事件写入失败: key={}", key, e);
        }
    }

    @Override
    public List<AdminRateLimitHitVO> listActiveHits(String ipFilter, int limit) {
        int max = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 300));
        String filter = StringUtils.hasText(ipFilter) ? ipFilter.trim() : null;
        List<AdminRateLimitHitVO> hits = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(RATE_LIMIT_PREFIX + "*").count(200).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext() && hits.size() < max) {
                String key = cursor.next();
                if (WHITELIST_KEY.equals(key)) {
                    continue;
                }
                AdminRateLimitHitVO hit = toHit(key);
                if (hit == null) {
                    continue;
                }
                if (filter != null) {
                    boolean match = (hit.getIp() != null && hit.getIp().contains(filter))
                            || (hit.getRedisKey() != null && hit.getRedisKey().contains(filter));
                    if (!match) {
                        continue;
                    }
                }
                hits.add(hit);
            }
        }
        return hits;
    }

    @Override
    public int clearIpRateLimits(String ip) {
        if (!StringUtils.hasText(ip)) {
            throw new CustomException(400, "IP 不能为空");
        }
        String normalized = ip.trim();
        Set<String> toDelete = new HashSet<>();
        String needle = "ip:" + normalized;
        ScanOptions options = ScanOptions.scanOptions().match(RATE_LIMIT_PREFIX + "*").count(200).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (WHITELIST_KEY.equals(key)) {
                    continue;
                }
                if (key.contains(needle) || key.endsWith(":" + normalized)) {
                    toDelete.add(key);
                }
            }
        }
        int deleted = 0;
        for (String key : toDelete) {
            Boolean ok = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(ok)) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public List<String> listWhitelist() {
        Set<String> members = redisTemplate.opsForSet().members(WHITELIST_KEY);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().sorted().toList();
    }

    @Override
    public void addWhitelist(String ip) {
        String normalized = requireIp(ip);
        redisTemplate.opsForSet().add(WHITELIST_KEY, normalized);
    }

    @Override
    public void removeWhitelist(String ip) {
        String normalized = requireIp(ip);
        redisTemplate.opsForSet().remove(WHITELIST_KEY, normalized);
    }

    @Override
    public boolean isWhitelisted(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        Boolean member = redisTemplate.opsForSet().isMember(WHITELIST_KEY, ip.trim());
        return Boolean.TRUE.equals(member);
    }

    private AdminRateLimitHitVO toHit(String redisKey) {
        if (!StringUtils.hasText(redisKey) || !redisKey.startsWith(RATE_LIMIT_PREFIX)) {
            return null;
        }
        String raw = redisKey.substring(RATE_LIMIT_PREFIX.length());
        String value = redisTemplate.opsForValue().get(redisKey);
        long count = 0L;
        if (value != null) {
            try {
                count = Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                count = 0L;
            }
        }
        Long ttl = redisTemplate.getExpire(redisKey);
        String scope = "unknown";
        String identity = raw;
        String ip = extractIpFromKey(raw);
        if (raw.startsWith("biz:")) {
            String rest = raw.substring(4);
            int idx = rest.indexOf(':');
            if (idx > 0) {
                scope = rest.substring(0, idx);
                identity = rest.substring(idx + 1);
            } else {
                scope = rest;
            }
        } else if (raw.startsWith(LOGIN_FAIL_PREFIX)) {
            scope = "login-fail";
            identity = raw.substring(LOGIN_FAIL_PREFIX.length());
        } else if (raw.startsWith(REFRESH_FAIL_PREFIX)) {
            scope = "refresh-fail";
            identity = raw.substring(REFRESH_FAIL_PREFIX.length());
        } else if (raw.startsWith("register:")) {
            scope = "register";
            identity = raw.substring("register:".length());
        } else {
            int idx = raw.indexOf(':');
            if (idx > 0) {
                scope = raw.substring(0, idx);
                identity = raw.substring(idx + 1);
            }
        }
        return AdminRateLimitHitVO.builder()
                .redisKey(redisKey)
                .scope(scope)
                .ip(ip)
                .identity(identity)
                .count(count)
                .ttlSeconds(ttl != null && ttl >= 0 ? ttl : null)
                .build();
    }

    private static String extractIpFromKey(String keyOrRaw) {
        if (!StringUtils.hasText(keyOrRaw)) {
            return null;
        }
        int idx = keyOrRaw.indexOf("ip:");
        if (idx < 0) {
            return null;
        }
        String rest = keyOrRaw.substring(idx + 3);
        int colon = rest.indexOf(':');
        return colon > 0 ? rest.substring(0, colon) : rest;
    }

    private static String requireIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            throw new CustomException(400, "IP 不能为空");
        }
        String normalized = ip.trim();
        if (normalized.length() > 64 || normalized.contains(" ") || normalized.contains("*")) {
            throw new CustomException(400, "IP 格式无效");
        }
        return normalized;
    }
}
