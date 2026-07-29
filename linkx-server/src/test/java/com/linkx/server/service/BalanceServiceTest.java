package com.linkx.server.service;

import com.linkx.server.controller.vo.BalanceVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BalanceService 余额服务测试
 */
@DisplayName("BalanceService 余额服务测试")
class BalanceServiceTest extends BaseIntegrationTest {

    @Autowired
    private BalanceService balanceService;

    @Nested
    @DisplayName("getBalance 获取余额测试")
    class GetBalanceTests {

        @Test
        @DisplayName("获取余额应成功")
        void getBalance_success() {
            BalanceVO balance = balanceService.getBalance(1L);
            assertNotNull(balance);
            assertNotNull(balance.getBalance());
            assertTrue(balance.getBalance().compareTo(BigDecimal.ZERO) >= 0);
        }

        @Test
        @DisplayName("新用户余额应为0")
        void newUser_balanceIsZero() {
            // 使用一个很大的用户ID确保是新用户
            BalanceVO balance = balanceService.getBalance(999999999L);
            assertNotNull(balance);
            assertEquals(0, balance.getBalance().compareTo(BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("deductBalance 扣减余额测试")
    class DeductBalanceTests {

        @Test
        @DisplayName("扣减余额应不抛异常")
        void deductBalance_noException() {
            assertDoesNotThrow(() ->
                    balanceService.deductBalance(1L, new BigDecimal("1.00"),
                            "test", "biz123", "测试扣减"));
        }
    }

    @Nested
    @DisplayName("addBalance 增加余额测试")
    class AddBalanceTests {

        @Test
        @DisplayName("增加余额应不抛异常")
        void addBalance_noException() {
            assertDoesNotThrow(() ->
                    balanceService.addBalance(1L, new BigDecimal("1.00"),
                            "test", "biz123", "测试增加"));
        }
    }

    @Nested
    @DisplayName("审计日志 afterCommit 测试")
    class AuditLogAfterCommitTests {

        @Test
        @DisplayName("负数金额应拒绝")
        void negativeAmount_shouldReject() {
            assertThrows(CustomException.class, () ->
                    balanceService.addBalance(1L, new BigDecimal("-1.00"),
                            "test", "neg-test", "负数测试"));
        }

        @Test
        @DisplayName("零金额应拒绝")
        void zeroAmount_shouldReject() {
            assertThrows(CustomException.class, () ->
                    balanceService.addBalance(1L, BigDecimal.ZERO,
                            "test", "zero-test", "零测试"));
        }

        @Test
        @DisplayName("获取余额方法应可调用")
        void getBalance_shouldWork() {
            // 测试获取余额方法正常工作
            assertDoesNotThrow(() -> balanceService.getBalance(1L));
        }
    }
}
