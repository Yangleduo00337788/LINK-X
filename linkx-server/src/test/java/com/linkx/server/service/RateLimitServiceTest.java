package com.linkx.server.service;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.impl.RateLimitServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * 限流服务单元测试
 * 包含 IP + 用户名双重限流测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("限流服务测试")
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private LinkxProperties linkxProperties;

    @Mock
    private LinkxProperties.Auth auth;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RateLimitServiceImpl rateLimitService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(linkxProperties.getAuth()).thenReturn(auth);
        lenient().when(auth.getLoginMaxAttempts()).thenReturn(5);
        lenient().when(auth.getLockDurationMinutes()).thenReturn(15);
        lenient().when(auth.getRateLimitRegisterPerMinute()).thenReturn(5);
    }

    @Test
    @DisplayName("通用限流 - 未超限时应通过")
    void shouldPassWhenUnderLimit() {
        // Arrange
        String key = "test:key";
        when(valueOperations.increment("linkx:rate:" + key)).thenReturn(3L);

        // Act & Assert
        assertDoesNotThrow(() -> rateLimitService.check(key, 5, 60));
    }

    @Test
    @DisplayName("通用限流 - 超过限制应抛出异常")
    void shouldThrowExceptionWhenOverLimit() {
        // Arrange
        String key = "test:key";
        when(valueOperations.increment("linkx:rate:" + key)).thenReturn(6L);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> rateLimitService.check(key, 5, 60));
        assertEquals(429, exception.getCode());
        assertTrue(exception.getMessage().contains("操作过于频繁"));
    }

    @Test
    @DisplayName("通用限流 - 首次访问应设置过期时间")
    void shouldSetExpireTimeOnFirstAccess() {
        // Arrange
        String key = "test:key";
        when(valueOperations.increment("linkx:rate:" + key)).thenReturn(1L);

        // Act
        rateLimitService.check(key, 5, 60);

        // Assert
        verify(redisTemplate).expire("linkx:rate:" + key, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("登录限流 - IP 级别限流")
    void shouldLimitByIp() {
        // Arrange
        String username = "testuser";
        String ip = "192.168.1.1";
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);

        // 模拟 IP 级别第 16 次请求（超过 5*3=15）
        when(valueOperations.increment("linkx:rate:linkx:login:fail:ip:" + ip))
                .thenReturn(16L);
        when(valueOperations.increment("linkx:rate:linkx:login:fail:" + username))
                .thenReturn(1L);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> rateLimitService.checkLoginRateLimit(username, request));
        assertEquals(429, exception.getCode());
        assertTrue(exception.getMessage().contains("该IP登录尝试过多"));
    }

    @Test
    @DisplayName("登录限流 - 用户名级别限流并锁定")
    void shouldLimitByUsernameAndLock() {
        // Arrange
        String username = "testuser";
        String ip = "192.168.1.1";
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);

        // 模拟用户名级别第 6 次请求（超过 5）
        when(valueOperations.increment("linkx:rate:linkx:login:fail:ip:" + ip))
                .thenReturn(1L);
        when(valueOperations.increment("linkx:rate:linkx:login:fail:" + username))
                .thenReturn(6L);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> rateLimitService.checkLoginRateLimit(username, request));
        assertEquals(429, exception.getCode());
        assertTrue(exception.getMessage().contains("登录失败次数过多"));

        // 验证设置锁定标记
        verify(valueOperations).set("linkx:login:lock:" + username, "1", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("登录限流 - 首次访问应设置过期时间")
    void shouldSetExpireOnFirstLoginAttempt() {
        // Arrange
        String username = "testuser";
        String ip = "192.168.1.1";
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);

        when(valueOperations.increment("linkx:rate:linkx:login:fail:ip:" + ip))
                .thenReturn(1L);
        when(valueOperations.increment("linkx:rate:linkx:login:fail:" + username))
                .thenReturn(1L);

        // Act
        rateLimitService.checkLoginRateLimit(username, request);

        // Assert
        verify(redisTemplate, times(2)).expire(anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("注册限流 - IP 级别限制")
    void shouldLimitRegisterByIp() {
        // Arrange
        String ip = "192.168.1.1";
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);

        // 模拟 IP 级别第 6 次注册请求
        when(valueOperations.increment("linkx:rate:register:ip:" + ip))
                .thenReturn(6L);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> rateLimitService.checkRegisterRateLimit(request));
        assertEquals(429, exception.getCode());
        assertTrue(exception.getMessage().contains("注册过于频繁"));
    }

    @Test
    @DisplayName("账号锁定检查 - 账号被锁定应返回 true")
    void shouldReturnTrueWhenAccountLocked() {
        // Arrange
        String username = "testuser";
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(redisTemplate.hasKey("linkx:login:lock:" + username)).thenReturn(true);

        // Act
        boolean locked = rateLimitService.isAccountLocked(username, request);

        // Assert
        assertTrue(locked);
    }

    @Test
    @DisplayName("账号锁定检查 - 账号未锁定应返回 false")
    void shouldReturnFalseWhenAccountNotLocked() {
        // Arrange
        String username = "testuser";
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(redisTemplate.hasKey("linkx:login:lock:" + username)).thenReturn(false);

        // Act
        boolean locked = rateLimitService.isAccountLocked(username, request);

        // Assert
        assertFalse(locked);
    }

    @Test
    @DisplayName("清除登录失败 - 应删除相关键")
    void shouldClearLoginFailure() {
        // Arrange
        String username = "testuser";
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        // Act
        rateLimitService.clearLoginFailure(username, request);

        // Assert
        verify(redisTemplate).delete("linkx:rate:linkx:login:fail:" + username);
        verify(redisTemplate).delete("linkx:login:lock:" + username);
    }

    @Test
    @DisplayName("获取客户端 IP - X-Forwarded-For 优先")
    void shouldPreferXForwardedForHeader() {
        // Arrange
        String forwardedIp = "10.0.0.1, 192.168.1.1";
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedIp);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        // Act - 调用 checkLoginRateLimit 会触发 getClientIp
        when(valueOperations.increment(anyString())).thenReturn(1L);
        rateLimitService.checkLoginRateLimit("user", request);

        // Assert - 应该使用第一个 IP
        verify(valueOperations).increment("linkx:rate:linkx:login:fail:ip:" + "10.0.0.1");
    }

    @Test
    @DisplayName("获取客户端 IP - X-Real-IP 次之")
    void shouldUseXRealIpWhenXForwardedForNotPresent() {
        // Arrange
        String realIp = "10.0.0.2";
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(realIp);

        // Act
        when(valueOperations.increment(anyString())).thenReturn(1L);
        rateLimitService.checkLoginRateLimit("user", request);

        // Assert
        verify(valueOperations).increment("linkx:rate:linkx:login:fail:ip:" + realIp);
    }

    @Test
    @DisplayName("获取客户端 IP - RemoteAddr 兜底")
    void shouldUseRemoteAddrAsFallback() {
        // Arrange
        String remoteIp = "192.168.1.100";
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(remoteIp);

        // Act
        when(valueOperations.increment(anyString())).thenReturn(1L);
        rateLimitService.checkLoginRateLimit("user", request);

        // Assert
        verify(valueOperations).increment("linkx:rate:linkx:login:fail:ip:" + remoteIp);
    }

    @Test
    @DisplayName("并发登录限流 - 竞态条件测试")
    void shouldHandleConcurrentLoginRaceCondition() throws InterruptedException {
        // Arrange
        String username = "concurrentUser";
        String ip = "192.168.1.1";
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);

        int threadCount = 10;
        AtomicInteger counter = new AtomicInteger(0);

        // 模拟 Redis 递增
        when(valueOperations.increment("linkx:rate:linkx:login:fail:ip:" + ip))
                .thenAnswer(invocation -> (long) counter.incrementAndGet());
        when(valueOperations.increment("linkx:rate:linkx:login:fail:" + username))
                .thenAnswer(invocation -> (long) counter.incrementAndGet());

        // Act
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    rateLimitService.checkLoginRateLimit(username, request);
                    successCount.incrementAndGet();
                } catch (CustomException e) {
                    if (e.getCode() == 429) {
                        rateLimitCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - 部分请求应该被限流
        assertTrue(rateLimitCount.get() > 0, "应该有部分请求被限流");
        assertTrue(successCount.get() <= 5, "成功请求不应超过限制");
    }
}
