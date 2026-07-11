package com.linkx.server.service.impl;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.controller.vo.UserInfoVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.TokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String ACCESS_KEY_PREFIX = "linkx:token:access:";
    private static final String REFRESH_KEY_PREFIX = "linkx:token:refresh:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final SysUserMapper sysUserMapper;
    private final LinkxProperties linkxProperties;

    @Override
    public TokenVO issueTokenPair(SysUser user) {
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        long accessExpire = linkxProperties.getJwt().getAccessExpire();
        long refreshExpire = linkxProperties.getJwt().getRefreshExpire();

        String accessToken = jwtUtils.generateToken(
                user.getId(), user.getUsername(), TokenType.ACCESS, accessJti, accessExpire);
        String refreshToken = jwtUtils.generateToken(
                user.getId(), user.getUsername(), TokenType.REFRESH, refreshJti, refreshExpire);

        storeAccessToken(accessJti, user.getId(), accessExpire);
        storeRefreshToken(refreshJti, user.getId(), refreshExpire);

        return buildTokenVO(user, accessToken, refreshToken, accessExpire);
    }

    @Override
    public TokenVO refreshAccessToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new CustomException(401, "refreshToken 无效");
        }

        Claims claims = parseClaims(refreshToken);
        if (jwtUtils.getTokenType(refreshToken) != TokenType.REFRESH) {
            throw new CustomException(401, "refreshToken 无效");
        }

        String refreshJti = claims.getId();
        String refreshKey = REFRESH_KEY_PREFIX + refreshJti;
        String userIdValue = redisTemplate.opsForValue().get(refreshKey);
        if (!StringUtils.hasText(userIdValue)) {
            throw new CustomException(401, "refreshToken 已失效");
        }

        Long userId = Long.valueOf(userIdValue);
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null || user.getStatus() != 1) {
            redisTemplate.delete(refreshKey);
            throw new CustomException(401, "账号不可用");
        }

        redisTemplate.delete(refreshKey);
        return issueTokenPair(user);
    }

    @Override
    public void logout(String authorization, String refreshToken) {
        revokeBearerToken(authorization);
        revokeRawToken(refreshToken, TokenType.REFRESH);
    }

    @Override
    public void assertAccessTokenActive(String accessToken) {
        Claims claims = parseClaims(accessToken);
        if (jwtUtils.getTokenType(accessToken) != TokenType.ACCESS) {
            throw new CustomException(401, "无效的访问令牌");
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
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + jti,
                String.valueOf(userId),
                Duration.ofMillis(expireMs));
    }

    private TokenVO buildTokenVO(SysUser user, String accessToken, String refreshToken, long accessExpire) {
        UserInfoVO userInfo = UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
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
