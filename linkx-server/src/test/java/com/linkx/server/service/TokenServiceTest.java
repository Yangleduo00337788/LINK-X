package com.linkx.server.service;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.impl.TokenServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Token 服务单元测试
 * 包含 Token 刷新竞态条件测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Token 服务测试")
class TokenServiceTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private LinkxProperties linkxProperties;

    @Mock
    private LinkxProperties.Jwt jwt;

    @Mock
    private Claims claims;

    @InjectMocks
    private TokenServiceImpl tokenService;

    private static final String TEST_SECRET = "test-secret-key-for-jwt-token-generation-at-least-32-chars-long";
    private static final Long TEST_USER_ID = 12345L;
    private static final String TEST_USERNAME = "testuser";
    private static final String ACCESS_JTI = "access-jti-123";
    private static final String REFRESH_JTI = "refresh-jti-456";

    @BeforeEach
    void setUp() {
        lenient().when(linkxProperties.getJwt()).thenReturn(jwt);
        lenient().when(jwt.getAccessExpire()).thenReturn(7200000L);
        lenient().when(jwt.getRefreshExpire()).thenReturn(604800000L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("发放 Token 对 - 应生成有效的 Access 和 Refresh Token")
    void shouldIssueTokenPair() {
        // Arrange
        SysUser user = createTestUser();

        when(jwtUtils.generateToken(eq(TEST_USER_ID), eq(TEST_USERNAME), eq(TokenType.ACCESS), anyString(), anyLong()))
                .thenReturn("access-token");
        when(jwtUtils.generateToken(eq(TEST_USER_ID), eq(TEST_USERNAME), eq(TokenType.REFRESH), anyString(), anyLong()))
                .thenReturn("refresh-token");

        // Act
        TokenVO tokenVO = tokenService.issueTokenPair(user);

        // Assert
        assertNotNull(tokenVO);
        assertEquals("access-token", tokenVO.getAccessToken());
        assertEquals("refresh-token", tokenVO.getRefreshToken());
        assertNotNull(tokenVO.getExpireTime());
        assertNotNull(tokenVO.getUser());
        assertEquals(TEST_USER_ID, tokenVO.getUser().getId());

        // Verify Redis storage
        verify(valueOperations, times(2)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("刷新 Access Token - 应成功生成新的 Token 对")
    void shouldRefreshAccessToken() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        SysUser user = createTestUser();

        when(jwtUtils.parseToken(refreshToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(refreshToken)).thenReturn(TokenType.REFRESH);
        when(claims.getId()).thenReturn(REFRESH_JTI);

        // 模拟分布式锁获取成功
        when(valueOperations.setIfAbsent(eq("linkx:token:refresh:lock:" + REFRESH_JTI), eq("1"), any(Duration.class)))
                .thenReturn(true);

        // 模拟 Lua 脚本执行返回 userId
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(Collections.singletonList("linkx:token:refresh:" + REFRESH_JTI)), any()))
                .thenReturn(TEST_USER_ID.toString());

        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(user);

        when(jwtUtils.generateToken(eq(TEST_USER_ID), eq(TEST_USERNAME), eq(TokenType.ACCESS), anyString(), anyLong()))
                .thenReturn("new-access-token");
        when(jwtUtils.generateToken(eq(TEST_USER_ID), eq(TEST_USERNAME), eq(TokenType.REFRESH), anyString(), anyLong()))
                .thenReturn("new-refresh-token");

        // Act
        TokenVO tokenVO = tokenService.refreshAccessToken(refreshToken);

        // Assert
        assertNotNull(tokenVO);
        assertEquals("new-access-token", tokenVO.getAccessToken());

        // Verify lock released
        verify(redisTemplate).delete("linkx:token:refresh:lock:" + REFRESH_JTI);
    }

    @Test
    @DisplayName("刷新 Access Token - 无效的 Refresh Token 应抛出异常")
    void shouldThrowExceptionForInvalidRefreshToken() {
        // Arrange
        String invalidToken = "invalid-token";
        when(jwtUtils.parseToken(invalidToken)).thenThrow(new RuntimeException("Invalid token"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> tokenService.refreshAccessToken(invalidToken));
        assertEquals(401, exception.getCode());
        assertTrue(exception.getMessage().contains("无效"));
    }

    @Test
    @DisplayName("刷新 Access Token - Access Token 不应用于刷新")
    void shouldThrowExceptionWhenUsingAccessToken() {
        // Arrange
        String accessToken = "access-token";
        when(jwtUtils.parseToken(accessToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(accessToken)).thenReturn(TokenType.ACCESS);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> tokenService.refreshAccessToken(accessToken));
        assertEquals(401, exception.getCode());
    }

    @Test
    @DisplayName("刷新 Access Token - 已失效的 Refresh Token 应抛出异常")
    void shouldThrowExceptionForExpiredRefreshToken() {
        // Arrange
        String expiredToken = "expired-refresh-token";
        when(jwtUtils.parseToken(expiredToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(expiredToken)).thenReturn(TokenType.REFRESH);
        when(claims.getId()).thenReturn(REFRESH_JTI);

        // 模拟分布式锁获取成功
        when(valueOperations.setIfAbsent(eq("linkx:token:refresh:lock:" + REFRESH_JTI), eq("1"), any(Duration.class)))
                .thenReturn(true);

        // 模拟 Lua 脚本执行返回 -1 (token 不存在)
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(Collections.singletonList("linkx:token:refresh:" + REFRESH_JTI)), any()))
                .thenReturn("-1");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> tokenService.refreshAccessToken(expiredToken));
        assertEquals(401, exception.getCode());
        assertTrue(exception.getMessage().contains("已失效"));

        // Verify lock released even on failure
        verify(redisTemplate).delete("linkx:token:refresh:lock:" + REFRESH_JTI);
    }

    @Test
    @DisplayName("刷新 Access Token - 账号已停用应抛出异常")
    void shouldThrowExceptionWhenUserDisabled() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        SysUser disabledUser = createTestUser();
        disabledUser.setStatus(0);

        when(jwtUtils.parseToken(refreshToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(refreshToken)).thenReturn(TokenType.REFRESH);
        when(claims.getId()).thenReturn(REFRESH_JTI);

        when(valueOperations.setIfAbsent(eq("linkx:token:refresh:lock:" + REFRESH_JTI), eq("1"), any(Duration.class)))
                .thenReturn(true);

        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(Collections.singletonList("linkx:token:refresh:" + REFRESH_JTI)), any()))
                .thenReturn(TEST_USER_ID.toString());

        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(disabledUser);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> tokenService.refreshAccessToken(refreshToken));
        assertEquals(401, exception.getCode());
        assertTrue(exception.getMessage().contains("账号不可用"));

        verify(redisTemplate).delete("linkx:token:refresh:lock:" + REFRESH_JTI);
    }

    @Test
    @DisplayName("刷新 Access Token - 并发刷新应被分布式锁阻止")
    void shouldBlockConcurrentRefreshWithDistributedLock() throws InterruptedException {
        // Arrange
        String refreshToken = "valid-refresh-token";
        SysUser user = createTestUser();

        when(jwtUtils.parseToken(refreshToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(refreshToken)).thenReturn(TokenType.REFRESH);
        when(claims.getId()).thenReturn(REFRESH_JTI);

        // 第一个线程获取锁成功，第二个线程获取锁失败
        AtomicInteger lockCounter = new AtomicInteger(0);
        when(valueOperations.setIfAbsent(eq("linkx:token:refresh:lock:" + REFRESH_JTI), eq("1"), any(Duration.class)))
                .thenAnswer(invocation -> lockCounter.incrementAndGet() == 1);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    tokenService.refreshAccessToken(refreshToken);
                    successCount.incrementAndGet();
                } catch (CustomException e) {
                    if (e.getCode() == 429 && e.getMessage().contains("频繁")) {
                        blockedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - 只有一个线程应该成功，其他应该被限流
        assertEquals(1, successCount.get(), "只有一个线程应该成功刷新 Token");
        assertEquals(threadCount - 1, blockedCount.get(), "其他线程应该被分布式锁阻止");
    }

    @Test
    @DisplayName("登出 - 应吊销 Access Token 和 Refresh Token")
    void shouldLogoutSuccessfully() {
        // Arrange
        String authorization = "Bearer valid-access-token";
        String refreshToken = "valid-refresh-token";

        when(jwtUtils.parseToken(anyString())).thenReturn(claims);
        when(claims.getId()).thenReturn(ACCESS_JTI);

        // Act
        tokenService.logout(authorization, refreshToken);

        // Assert
        // Verify that delete was called for both tokens
        verify(redisTemplate, atLeastOnce()).delete(anyString());
    }

    @Test
    @DisplayName("登出 - 无效的 Token 不应抛出异常")
    void shouldNotThrowExceptionForInvalidTokenOnLogout() {
        // Arrange
        String invalidToken = "invalid-token";
        when(jwtUtils.parseToken(invalidToken)).thenThrow(new RuntimeException("Invalid token"));

        // Act & Assert
        assertDoesNotThrow(() -> tokenService.logout("Bearer " + invalidToken, invalidToken));
    }

    @Test
    @DisplayName("验证 Access Token 活跃状态 - 有效的 Token 应通过")
    void shouldAssertAccessTokenActive() {
        // Arrange
        String accessToken = "valid-access-token";
        when(jwtUtils.parseToken(accessToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(accessToken)).thenReturn(TokenType.ACCESS);
        when(claims.getId()).thenReturn(ACCESS_JTI);
        when(redisTemplate.hasKey("linkx:token:access:" + ACCESS_JTI)).thenReturn(true);

        // Act & Assert
        assertDoesNotThrow(() -> tokenService.assertAccessTokenActive(accessToken));
    }

    @Test
    @DisplayName("验证 Access Token 活跃状态 - 不存在的 Token 应抛出异常")
    void shouldThrowExceptionForInactiveAccessToken() {
        // Arrange
        String accessToken = "expired-access-token";
        when(jwtUtils.parseToken(accessToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(accessToken)).thenReturn(TokenType.ACCESS);
        when(claims.getId()).thenReturn(ACCESS_JTI);
        when(redisTemplate.hasKey("linkx:token:access:" + ACCESS_JTI)).thenReturn(false);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> tokenService.assertAccessTokenActive(accessToken));
        assertEquals(401, exception.getCode());
        assertTrue(exception.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("验证 Access Token 活跃状态 - Refresh Token 不应通过")
    void shouldThrowExceptionWhenUsingRefreshTokenAsAccess() {
        // Arrange
        String refreshToken = "refresh-token";
        when(jwtUtils.parseToken(refreshToken)).thenReturn(claims);
        when(jwtUtils.getTokenType(refreshToken)).thenReturn(TokenType.REFRESH);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> tokenService.assertAccessTokenActive(refreshToken));
        assertEquals(401, exception.getCode());
    }

    private SysUser createTestUser() {
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setNickname("Test User");
        user.setAvatar("/avatar.png");
        user.setStatus(1);
        return user;
    }
}
