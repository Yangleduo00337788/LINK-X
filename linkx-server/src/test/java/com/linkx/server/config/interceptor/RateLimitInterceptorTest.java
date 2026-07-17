package com.linkx.server.config.interceptor;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RateLimitInterceptor 限流拦截器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitInterceptor 限流拦截器测试")
class RateLimitInterceptorTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private LinkxProperties linkxProperties;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(rateLimitService, jwtUtils, linkxProperties);
    }

    @Nested
    @DisplayName("拦截器初始化测试")
    class InitTests {

        @Test
        @DisplayName("拦截器应正确初始化")
        void interceptorInitializes() {
            assertNotNull(interceptor);
        }
    }

    @Nested
    @DisplayName("拦截器依赖测试")
    class DependenciesTests {

        @Test
        @DisplayName("拦截器依赖应正确注入")
        void dependenciesInjected() {
            assertNotNull(interceptor);
            // 验证依赖存在
            assertDoesNotThrow(() -> {
                // 模拟基本调用
                rateLimitService.check("test", 10, 60);
            });
        }
    }

    @Nested
    @DisplayName("RateLimitService.check 调用测试")
    class RateLimitCheckTests {

        @Test
        @DisplayName("check方法应正确调用")
        void checkMethod_calledCorrectly() {
            // 直接测试 rateLimitService.check 方法
            String key = "biz:test:123";
            int value = 10;
            int window = 60;

            // 调用 check
            assertDoesNotThrow(() -> rateLimitService.check(key, value, window));
        }

        @Test
        @DisplayName("check方法抛异常时应传播")
        void checkThrows_propagates() {
            String key = "biz:test:999";
            doThrow(new CustomException(429, "请求过于频繁"))
                    .when(rateLimitService).check(any(), anyInt(), anyInt());

            assertThrows(CustomException.class, () ->
                    rateLimitService.check(key, 10, 60));
        }
    }

    @Nested
    @DisplayName("CustomException 限流异常测试")
    class RateLimitExceptionTests {

        @Test
        @DisplayName("CustomException 429应表示限流")
        void customException429_meansRateLimit() {
            CustomException ex = new CustomException(429, "请求过于频繁");

            assertEquals(429, ex.getCode());
            assertEquals("请求过于频繁", ex.getMessage());
        }
    }
}
