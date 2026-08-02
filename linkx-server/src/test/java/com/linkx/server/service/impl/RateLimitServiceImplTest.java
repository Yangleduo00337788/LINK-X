package com.linkx.server.service.impl;

import com.linkx.server.common.LoginSide;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.admin.AdminRiskEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RateLimitServiceImpl 限流")
class RateLimitServiceImplTest {

    private static final String IP = "203.0.113.10";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SetOperations<String, String> setOps;
    @Mock AdminRiskEventService adminRiskEventService;
    @Mock HttpServletRequest request;
    @Mock Cursor<String> cursor;

    private RateLimitServiceImpl service;
    private LinkxProperties props;

    @BeforeEach
    void setUp() {
        props = new LinkxProperties();
        props.getAuth().setLoginMaxAttempts(5);
        props.getAuth().setLockDurationMinutes(10);
        props.getAuth().setRateLimitRegisterPerMinute(3);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(request.getRemoteAddr()).thenReturn(IP);
        when(request.getHeader(anyString())).thenReturn(null);
        service = new RateLimitServiceImpl(redisTemplate, props, adminRiskEventService);
    }

    private void mockIncr(long count) {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(count);
    }

    @Nested
    @DisplayName("check 通用限流")
    class Check {
        @Test
        @DisplayName("未超限放行")
        void underLimit() {
            mockIncr(2L);
            assertDoesNotThrow(() -> service.check("biz:send:42", 5, 60));
        }

        @Test
        @DisplayName("超限抛 429")
        void overLimit() {
            mockIncr(6L);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.check("biz:send:42", 5, 60));
            assertEquals(429, ex.getCode());
            verify(adminRiskEventService).recordRateLimit(eq(42L), eq("42"), eq("send"), isNull());
        }

        @Test
        @DisplayName("白名单 IP 跳过")
        void whitelistedIp() {
            when(setOps.isMember(eq("linkx:rate:whitelist"), eq(IP))).thenReturn(true);
            service.check("biz:api:ip:" + IP, 1, 60);
            verify(redisTemplate, never()).execute(any(), anyList(), any());
        }
    }

    @Nested
    @DisplayName("登录限流")
    class Login {
        @Test
        @DisplayName("未达锁定阈值返回 false")
        void notLockedYet() {
            mockIncr(2L);
            assertFalse(service.checkLoginRateLimit("alice", request, LoginSide.CLIENT));
        }

        @Test
        @DisplayName("用户名达阈值锁定")
        void userLocked() {
            // IP 计数、用户名计数各一次 atomicIncr
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L, 5L);
            assertTrue(service.checkLoginRateLimit("alice", request, LoginSide.CLIENT));
            verify(valueOps).set(startsWith("linkx:login:lock:"), eq("1"), any());
        }

        @Test
        @DisplayName("IP 超限抛 429")
        void ipBlocked() {
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(16L);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.checkLoginRateLimit("alice", request, LoginSide.CLIENT));
            assertEquals(429, ex.getCode());
        }

        @Test
        @DisplayName("isAccountLocked / clearLoginFailure")
        void lockState() {
            when(redisTemplate.hasKey("linkx:login:lock:client:alice")).thenReturn(true);
            assertTrue(service.isAccountLocked("alice", LoginSide.CLIENT));
            service.clearLoginFailure("alice", LoginSide.CLIENT);
            verify(redisTemplate).delete("linkx:rate:linkx:login:fail:client:alice");
            verify(redisTemplate).delete("linkx:login:lock:client:alice");
        }
    }

    @Nested
    @DisplayName("注册与 refresh")
    class RegisterAndRefresh {
        @Test
        @DisplayName("注册未超限")
        void registerOk() {
            mockIncr(1L);
            assertDoesNotThrow(() -> service.checkRegisterRateLimit(request));
        }

        @Test
        @DisplayName("注册超限")
        void registerBlocked() {
            mockIncr(4L);
            assertThrows(CustomException.class, () -> service.checkRegisterRateLimit(request));
        }

        @Test
        @DisplayName("refresh 失败未达阈值")
        void refreshOk() {
            mockIncr(3L);
            assertDoesNotThrow(() -> service.recordRefreshFailure(request));
        }

        @Test
        @DisplayName("refresh 失败过多")
        void refreshBlocked() {
            mockIncr(10L);
            assertThrows(CustomException.class, () -> service.recordRefreshFailure(request));
            verify(adminRiskEventService).recordRateLimit(isNull(), eq("ip:" + IP), eq("refresh-token"), eq(IP));
        }
    }

    @Nested
    @DisplayName("白名单与扫描")
    class WhitelistAndScan {
        @Test
        @DisplayName("白名单增删查")
        void whitelistCrud() {
            when(setOps.members("linkx:rate:whitelist")).thenReturn(Set.of("1.2.3.4", IP));
            service.addWhitelist(IP);
            service.removeWhitelist(IP);
            List<String> list = service.listWhitelist();
            assertEquals(List.of("1.2.3.4", IP), list);
            when(setOps.isMember("linkx:rate:whitelist", IP)).thenReturn(true);
            assertTrue(service.isWhitelisted(IP));
        }

        @Test
        @DisplayName("listActiveHits 扫描命中")
        void listHits() {
            String redisKey = "linkx:rate:register:ip:" + IP;
            when(redisTemplate.scan(any())).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(true, false);
            when(cursor.next()).thenReturn(redisKey);
            when(valueOps.get(redisKey)).thenReturn("4");
            when(redisTemplate.getExpire(redisKey)).thenReturn(55L);

            var hits = service.listActiveHits(IP, 10);

            assertEquals(1, hits.size());
            assertEquals("register", hits.get(0).getScope());
            assertEquals(IP, hits.get(0).getIp());
        }

        @Test
        @DisplayName("clearIpRateLimits 删除匹配键")
        void clearIp() {
            String key = "linkx:rate:register:ip:" + IP;
            when(redisTemplate.scan(any())).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(true, false);
            when(cursor.next()).thenReturn(key);
            when(redisTemplate.delete(key)).thenReturn(true);

            int deleted = service.clearIpRateLimits(IP);

            assertEquals(1, deleted);
        }
    }
}
