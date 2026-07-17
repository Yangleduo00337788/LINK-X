package com.linkx.server.service;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimitService 限流服务测试
 */
@DisplayName("RateLimitService 限流服务测试")
class RateLimitServiceTest extends BaseIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Nested
    @DisplayName("check 限流检查测试")
    class CheckTests {

        @Test
        @DisplayName("首次请求不应触发限流")
        void firstRequest_notLimited() {
            String key = "test:check:first:" + System.nanoTime();
            assertDoesNotThrow(() -> rateLimitService.check(key, 10, 60));
        }
    }

    @Nested
    @DisplayName("账号锁定测试")
    class AccountLockTests {

        @Test
        @DisplayName("clearLoginFailure应正常执行")
        void clearLoginFailure_works() {
            String username = "clear_" + System.nanoTime();
            // 清除不应抛异常
            assertDoesNotThrow(() -> rateLimitService.clearLoginFailure(username, null));
        }
    }
}
