package com.linkx.server.service.impl;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.metrics.LinkxMetrics;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.MediaUrlService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenServiceImpl 单元测试")
class TokenServiceImplTest {

    private static final String JWT_SECRET = "Test-Local-JWT-Key-For-Integration-2026!!";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SetOperations<String, String> setOps;
    @Mock SysUserMapper sysUserMapper;
    @Mock MediaUrlService mediaUrlService;
    @Mock LinkxMetrics linkxMetrics;

    private JwtUtils jwtUtils;
    private LinkxProperties linkxProperties;
    private TokenServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getJwt().setSecret(JWT_SECRET);
        linkxProperties.getJwt().setAccessExpire(3_600_000L);
        linkxProperties.getJwt().setRefreshExpire(86_400_000L);

        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", JWT_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "accessExpire", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpire", 86_400_000L);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));

        service = new TokenServiceImpl(jwtUtils, redisTemplate, sysUserMapper, linkxProperties, mediaUrlService, linkxMetrics);
    }

    private SysUser activeUser() {
        return SysUser.builder().id(42L).username("tokuser").nickname("Tok").avatar("av").status(1).build();
    }

    @Test
    @DisplayName("issueTokenPair 绑定设备并清除踢下线标记")
    void issueWithDevice() {
        doNothing().when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(setOps.add(anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        TokenVO vo = service.issueTokenPair(activeUser(), " device-1 ");
        assertNotNull(vo.getAccessToken());
        verify(redisTemplate).delete("linkx:device:kicked:42:device-1");
    }

    @Test
    @DisplayName("refreshAccessToken 锁冲突 429")
    void refreshLockConflict() {
        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = jwtUtils.generateToken(42L, "tokuser", TokenType.REFRESH, refreshJti, 86_400_000L);
        when(valueOps.setIfAbsent(startsWith("linkx:token:refresh:lock:"), anyString(), any(Duration.class)))
                .thenReturn(false);

        CustomException ex = assertThrows(CustomException.class, () -> service.refreshAccessToken(refreshToken));
        assertEquals(429, ex.getCode());
    }

    @Test
    @DisplayName("refreshAccessToken 设备被踢 401")
    void refreshDeviceKicked() {
        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = jwtUtils.generateToken(42L, "tokuser", TokenType.REFRESH, refreshJti, 86_400_000L);
        when(valueOps.setIfAbsent(startsWith("linkx:token:refresh:lock:"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn("42");
        when(sysUserMapper.selectOneById(42L)).thenReturn(activeUser());
        when(redisTemplate.hasKey("linkx:device:kicked:42:phone-1")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.refreshAccessToken(refreshToken, "phone-1"));
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("revokeAllUserTokens 清理 refresh 集合")
    void revokeAll() {
        when(setOps.members("linkx:user:refresh-set:42")).thenReturn(Set.of("jti-1", "jti-2"));
        service.revokeAllUserTokens(42L);
        verify(redisTemplate).delete(argThat((List<String> keys) ->
                keys.size() == 2
                        && keys.contains("linkx:token:refresh:jti-1")
                        && keys.contains("linkx:token:refresh:jti-2")));
        verify(redisTemplate).delete("linkx:user:refresh-set:42");
    }

    @Test
    @DisplayName("revokeDeviceTokens 标记踢下线")
    void revokeDevice() {
        when(setOps.members("linkx:device:access-set:42:dev1")).thenReturn(Set.of("a1"));
        when(setOps.members("linkx:device:refresh-set:42:dev1")).thenReturn(Set.of("r1"));
        when(setOps.remove(eq("linkx:user:refresh-set:42"), eq("r1"))).thenReturn(1L);

        service.revokeDeviceTokens(42L, "dev1");
        verify(redisTemplate).delete(List.of("linkx:token:access:a1"));
        verify(redisTemplate).delete(List.of("linkx:token:refresh:r1"));
        verify(valueOps).set(startsWith("linkx:device:kicked:42:dev1"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("assertAccessTokenActive 全局吊销后旧 token 失效")
    void assertRevokedUser() {
        String accessJti = UUID.randomUUID().toString();
        String accessToken = jwtUtils.generateToken(42L, "tokuser", TokenType.ACCESS, accessJti, 3_600_000L);
        Claims claims = jwtUtils.parseToken(accessToken);
        when(valueOps.get("linkx:user:token-revoked:42")).thenReturn(String.valueOf(claims.getIssuedAt().getTime() + 1000));
        when(redisTemplate.hasKey("linkx:token:access:" + accessJti)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.assertAccessTokenActive(accessToken));
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("logout refresh 用户不匹配")
    void logoutRefreshMismatch() {
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        String accessToken = jwtUtils.generateToken(42L, "tokuser", TokenType.ACCESS, accessJti, 3_600_000L);
        String refreshToken = jwtUtils.generateToken(99L, "other", TokenType.REFRESH, refreshJti, 86_400_000L);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.logout(accessToken, refreshToken));
        assertEquals(401, ex.getCode());
    }
}
