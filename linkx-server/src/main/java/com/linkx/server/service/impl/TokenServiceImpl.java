package com.linkx.server.service.impl;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.common.UserProfileMapper;
import com.linkx.server.controller.vo.UserInfoVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.TokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String ACCESS_KEY_PREFIX = "linkx:token:access:";
    private static final String REFRESH_KEY_PREFIX = "linkx:token:refresh:";
    private static final String REFRESH_LOCK_PREFIX = "linkx:token:refresh:lock:";
    private static final String USER_REFRESH_SET_PREFIX = "linkx:user:refresh-set:";
    private static final String DEVICE_ACCESS_SET_PREFIX = "linkx:device:access-set:";
    private static final String DEVICE_REFRESH_SET_PREFIX = "linkx:device:refresh-set:";
    private static final String DEVICE_KICKED_PREFIX = "linkx:device:kicked:";

    // Lua 脚本：原子性地验证并删除 refresh token
    private static final String REFRESH_TOKEN_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local expectedJti = ARGV[1] " +
            "local value = redis.call('get', key) " +
            "if not value then return -1 end " +  // -1: token 不存在或已过期
            "redis.call('del', key) " +
            "return value";  // 返回 userId

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final SysUserMapper sysUserMapper;
    private final LinkxProperties linkxProperties;
    private final MediaUrlService mediaUrlService;

    @Override
    public TokenVO issueTokenPair(SysUser user) {
        return issueTokenPair(user, null);
    }

    @Override
    public TokenVO issueTokenPair(SysUser user, String deviceId) {
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        long accessExpire = linkxProperties.getJwt().getAccessExpire();
        long refreshExpire = linkxProperties.getJwt().getRefreshExpire();

        String accessToken = jwtUtils.generateToken(
                user.getId(), user.getUsername(), TokenType.ACCESS, accessJti, accessExpire);
        String refreshToken = jwtUtils.generateToken(
                user.getId(), user.getUsername(), TokenType.REFRESH, refreshJti, refreshExpire);

        String normalizedDeviceId = normalizeDeviceId(deviceId);
        storeAccessToken(accessJti, user.getId(), accessExpire);
        storeRefreshToken(refreshJti, user.getId(), refreshExpire);
        bindTokenToDevice(user.getId(), normalizedDeviceId, accessJti, refreshJti, refreshExpire);
        if (normalizedDeviceId != null) {
            clearDeviceKick(user.getId(), normalizedDeviceId);
        }

        return buildTokenVO(user, accessToken, refreshToken, accessExpire);
    }

    @Override
    public TokenVO refreshAccessToken(String refreshToken) {
        return refreshAccessToken(refreshToken, null);
    }

    @Override
    public TokenVO refreshAccessToken(String refreshToken, String deviceId) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new CustomException(401, "refreshToken 无效");
        }

        // 先解析 token 获取 jti（不验证存储，仅解析 JWT）
        Claims claims;
        try {
            claims = jwtUtils.parseToken(refreshToken);
        } catch (Exception e) {
            throw new CustomException(401, "refreshToken 无效或已过期");
        }

        if (jwtUtils.getTokenType(refreshToken) != TokenType.REFRESH) {
            throw new CustomException(401, "refreshToken 无效");
        }

        String refreshJti = claims.getId();
        String refreshKey = REFRESH_KEY_PREFIX + refreshJti;
        String lockKey = REFRESH_LOCK_PREFIX + refreshJti;

        // 使用分布式锁防止并发刷新导致的 Token 重复发放问题
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(locked)) {
            throw new CustomException(429, "Token 刷新过于频繁，请稍后重试");
        }

        try {
            // 使用 Lua 脚本原子性地验证并删除 refresh token
            DefaultRedisScript<String> script = new DefaultRedisScript<>();
            script.setScriptText(REFRESH_TOKEN_LUA_SCRIPT);
            script.setResultType(String.class);

            String userIdValue = redisTemplate.execute(script, Collections.singletonList(refreshKey), refreshJti);

            if (userIdValue == null || "-1".equals(userIdValue)) {
                throw new CustomException(401, "refreshToken 已失效");
            }

            Long userId = Long.valueOf(userIdValue);
            SysUser user = sysUserMapper.selectOneById(userId);
            if (user == null || user.getStatus() != 1) {
                throw new CustomException(401, "账号不可用");
            }

            String normalizedDeviceId = normalizeDeviceId(deviceId);
            if (normalizedDeviceId != null && isDeviceKicked(userId, normalizedDeviceId)) {
                throw new CustomException(401, "设备已被强制下线，请重新登录");
            }

            return issueTokenPair(user, normalizedDeviceId);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new CustomException(401, "未提供访问令牌");
        }
        // 解析并校验 access token 必须属于 ACCESS 类型
        Claims accessClaims = parseClaims(accessToken);
        if (jwtUtils.getTokenType(accessToken) != TokenType.ACCESS) {
            throw new CustomException(401, "无效的访问令牌");
        }
        Long accessUserId = accessClaims.get("userId", Long.class);

        // 如果同时携带 refreshToken，必须属于同一用户，否则拒绝（防止用 A 的 token 吊销 B 的 refresh）
        if (StringUtils.hasText(refreshToken)) {
            try {
                if (jwtUtils.getTokenType(refreshToken) == TokenType.REFRESH) {
                    Claims refreshClaims = jwtUtils.parseToken(refreshToken);
                    Long refreshUserId = refreshClaims.get("userId", Long.class);
                    if (refreshUserId == null || !refreshUserId.equals(accessUserId)) {
                        throw new CustomException(401, "refreshToken 与当前用户不匹配");
                    }
                } else {
                    throw new CustomException(401, "refreshToken 类型错误");
                }
            } catch (CustomException e) {
                throw e;
            } catch (Exception e) {
                throw new CustomException(401, "refreshToken 无效");
            }
        }

        // 校验通过后吊销
        redisTemplate.delete(ACCESS_KEY_PREFIX + accessClaims.getId());
        revokeRawToken(refreshToken, TokenType.REFRESH);
    }

    @Override
    public void revokeAllUserTokens(Long userId) {
        String userRefreshSetKey = USER_REFRESH_SET_PREFIX + userId;

        // 获取用户所有 refresh token jti 并全部删除
        Set<String> jtis = redisTemplate.opsForSet().members(userRefreshSetKey);
        if (jtis != null && !jtis.isEmpty()) {
            List<String> refreshKeys = jtis.stream()
                    .map(jti -> REFRESH_KEY_PREFIX + jti)
                    .collect(Collectors.toList());
            redisTemplate.delete(refreshKeys);
        }
        // 删除用户的 refresh token 集合
        redisTemplate.delete(userRefreshSetKey);

        // 设置撤销标记：密码重置后所有旧 Token 失效
        String revokeKey = "linkx:user:token-revoked:" + userId;
        redisTemplate.opsForValue().set(revokeKey, String.valueOf(System.currentTimeMillis()),
                Duration.ofDays(30));
    }

    @Override
    public void revokeDeviceTokens(Long userId, String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        if (userId == null || normalized == null) {
            return;
        }

        String accessSetKey = DEVICE_ACCESS_SET_PREFIX + userId + ":" + normalized;
        String refreshSetKey = DEVICE_REFRESH_SET_PREFIX + userId + ":" + normalized;

        Set<String> accessJtis = redisTemplate.opsForSet().members(accessSetKey);
        if (accessJtis != null && !accessJtis.isEmpty()) {
            List<String> keys = accessJtis.stream()
                    .map(jti -> ACCESS_KEY_PREFIX + jti)
                    .collect(Collectors.toList());
            redisTemplate.delete(keys);
        }

        Set<String> refreshJtis = redisTemplate.opsForSet().members(refreshSetKey);
        if (refreshJtis != null && !refreshJtis.isEmpty()) {
            List<String> keys = refreshJtis.stream()
                    .map(jti -> REFRESH_KEY_PREFIX + jti)
                    .collect(Collectors.toList());
            redisTemplate.delete(keys);
            String userRefreshSetKey = USER_REFRESH_SET_PREFIX + userId;
            for (String jti : refreshJtis) {
                redisTemplate.opsForSet().remove(userRefreshSetKey, jti);
            }
        }

        redisTemplate.delete(accessSetKey);
        redisTemplate.delete(refreshSetKey);

        long ttl = Math.max(linkxProperties.getJwt().getRefreshExpire(), Duration.ofDays(1).toMillis());
        redisTemplate.opsForValue().set(
                DEVICE_KICKED_PREFIX + userId + ":" + normalized,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofMillis(ttl));
    }

    @Override
    public boolean isDeviceKicked(Long userId, String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        if (userId == null || normalized == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(DEVICE_KICKED_PREFIX + userId + ":" + normalized));
    }

    @Override
    public void clearDeviceKick(Long userId, String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        if (userId == null || normalized == null) {
            return;
        }
        redisTemplate.delete(DEVICE_KICKED_PREFIX + userId + ":" + normalized);
    }

    @Override
    public void assertAccessTokenActive(String accessToken) {
        assertAccessTokenActive(accessToken, null);
    }

    @Override
    public void assertAccessTokenActive(String accessToken, String deviceId) {
        Claims claims = parseClaims(accessToken);
        if (jwtUtils.getTokenType(accessToken) != TokenType.ACCESS) {
            throw new CustomException(401, "无效的访问令牌");
        }

        Long userId = claims.get("userId", Long.class);

        String normalizedDeviceId = normalizeDeviceId(deviceId);
        if (normalizedDeviceId != null && isDeviceKicked(userId, normalizedDeviceId)) {
            throw new CustomException(401, "设备已被强制下线，请重新登录");
        }

        // 检查用户是否被强制吊销了所有 Token
        String revokeKey = "linkx:user:token-revoked:" + userId;
        String revokedAt = redisTemplate.opsForValue().get(revokeKey);
        if (revokedAt != null) {
            Long tokenIssuedAt = claims.get("iat", Long.class) * 1000; // iat 是秒，转毫秒
            Long revokedAtMs = Long.valueOf(revokedAt);
            if (tokenIssuedAt < revokedAtMs) {
                throw new CustomException(401, "登录已失效，请重新登录");
            }
        }

        String accessKey = ACCESS_KEY_PREFIX + claims.getId();
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(accessKey))) {
            throw new CustomException(401, "登录已过期，请重新登录");
        }
    }

    private Claims parseClaims(String token) {
        try {
            return jwtUtils.parseToken(token);
        } catch (Exception e) {
            throw new CustomException(401, "令牌无效或已过期");
        }
    }

    private void revokeBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return;
        }
        String token = authorization;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        revokeRawToken(token, TokenType.ACCESS);
    }

    private void revokeRawToken(String token, TokenType expectedType) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            if (jwtUtils.getTokenType(token) != expectedType) {
                return;
            }
            Claims claims = jwtUtils.parseToken(token);
            String prefix = expectedType == TokenType.ACCESS ? ACCESS_KEY_PREFIX : REFRESH_KEY_PREFIX;
            redisTemplate.delete(prefix + claims.getId());
        } catch (Exception ignored) {
            // 登出时忽略无效 token
        }
    }

    private void storeAccessToken(String jti, Long userId, long expireMs) {
        redisTemplate.opsForValue().set(
                ACCESS_KEY_PREFIX + jti,
                String.valueOf(userId),
                Duration.ofMillis(expireMs));
    }

    private void storeRefreshToken(String jti, Long userId, long expireMs) {
        String userRefreshSetKey = USER_REFRESH_SET_PREFIX + userId;
        String refreshKey = REFRESH_KEY_PREFIX + jti;

        // 将 jti 加入用户的 refresh token 集合
        redisTemplate.opsForSet().add(userRefreshSetKey, jti);
        // 设置 refresh token 本身
        redisTemplate.opsForValue().set(refreshKey, String.valueOf(userId), Duration.ofMillis(expireMs));
    }

    private void bindTokenToDevice(Long userId, String deviceId, String accessJti, String refreshJti, long expireMs) {
        if (deviceId == null) {
            return;
        }
        String accessSetKey = DEVICE_ACCESS_SET_PREFIX + userId + ":" + deviceId;
        String refreshSetKey = DEVICE_REFRESH_SET_PREFIX + userId + ":" + deviceId;
        redisTemplate.opsForSet().add(accessSetKey, accessJti);
        redisTemplate.opsForSet().add(refreshSetKey, refreshJti);
        redisTemplate.expire(accessSetKey, Duration.ofMillis(expireMs));
        redisTemplate.expire(refreshSetKey, Duration.ofMillis(expireMs));
    }

    private String normalizeDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        String trimmed = deviceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TokenVO buildTokenVO(SysUser user, String accessToken, String refreshToken, long accessExpire) {
        UserInfoVO userInfo = UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(mediaUrlService.resolve(user.getAvatar()))
                .signature(user.getSignature())
                .build();

        return TokenVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expireTime(System.currentTimeMillis() + accessExpire)
                .user(userInfo)
                .build();
    }
}
