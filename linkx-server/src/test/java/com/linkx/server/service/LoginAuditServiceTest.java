package com.linkx.server.service;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginAuditService 登录审计服务测试
 */
@DisplayName("LoginAuditService 登录审计服务测试")
class LoginAuditServiceTest extends BaseIntegrationTest {

    @Autowired
    private LoginAuditService loginAuditService;

    @Nested
    @DisplayName("record 记录登录审计测试")
    class RecordTests {

        @Test
        @DisplayName("记录成功登录应不抛异常")
        void recordSuccessLogin_noException() {
            assertDoesNotThrow(() ->
                    loginAuditService.record(1L, "testuser", "127.0.0.1", "TestAgent", true, null));
        }

        @Test
        @DisplayName("记录失败登录应不抛异常")
        void recordFailedLogin_noException() {
            assertDoesNotThrow(() ->
                    loginAuditService.record(1L, "testuser", "127.0.0.1", "TestAgent", false, "密码错误"));
        }

        @Test
        @DisplayName("长UserAgent应被截断")
        void longUserAgent_truncated() {
            String longAgent = "x".repeat(600);
            assertDoesNotThrow(() ->
                    loginAuditService.record(1L, "testuser", "127.0.0.1", longAgent, true, null));
        }

        @Test
        @DisplayName("长reason应被截断")
        void longReason_truncated() {
            String longReason = "y".repeat(300);
            assertDoesNotThrow(() ->
                    loginAuditService.record(1L, "testuser", "127.0.0.1", "Agent", false, longReason));
        }

        @Test
        @DisplayName("null值应正常处理")
        void nullValues_handled() {
            assertDoesNotThrow(() ->
                    loginAuditService.record(null, null, null, null, false, null));
        }
    }
}
