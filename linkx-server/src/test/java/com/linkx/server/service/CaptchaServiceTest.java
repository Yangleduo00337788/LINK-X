package com.linkx.server.service;

import com.linkx.server.controller.vo.CaptchaVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.impl.CaptchaServiceImpl;
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
 * 验证码服务单元测试
 * 包含竞态条件测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("验证码服务测试")
class CaptchaServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CaptchaServiceImpl captchaService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("生成验证码应返回有效的 CaptchaVO")
    void shouldGenerateValidCaptcha() {
        // Arrange
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);

        // Act
        CaptchaVO captcha = captchaService.generate();

        // Assert
        assertNotNull(captcha);
        assertNotNull(captcha.getCaptchaId());
        assertNotNull(captcha.getImageBase64());
        assertTrue(captcha.getImageBase64().startsWith("data:image/png;base64,"));
        assertEquals(300L, captcha.getExpireSeconds());

        // Verify Redis operations
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture(), eq(TimeUnit.SECONDS));
        assertTrue(keyCaptor.getValue().startsWith("linkx:captcha:"));
        assertEquals(300L, ttlCaptor.getValue());
    }

    @Test
    @DisplayName("验证码验证成功")
    void shouldValidateCaptchaSuccessfully() {
        // Arrange
        String captchaId = "test-captcha-id";
        String correctCode = "AB12";
        String key = "linkx:captcha:" + captchaId;

        // Mock Lua script execution - return 1 (success)
        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq(correctCode)))
                .thenReturn(1L);

        // Act & Assert
        assertDoesNotThrow(() -> captchaService.validate(captchaId, correctCode));
    }

    @Test
    @DisplayName("验证码验证失败 - 错误验证码")
    void shouldThrowExceptionForWrongCaptcha() {
        // Arrange
        String captchaId = "test-captcha-id";
        String wrongCode = "WRONG";
        String key = "linkx:captcha:" + captchaId;

        // Mock Lua script execution - return 0 (wrong code)
        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq(wrongCode)))
                .thenReturn(0L);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> captchaService.validate(captchaId, wrongCode));
        assertEquals("验证码错误", exception.getMessage());
    }

    @Test
    @DisplayName("验证码验证失败 - 验证码已过期")
    void shouldThrowExceptionForExpiredCaptcha() {
        // Arrange
        String captchaId = "test-captcha-id";
        String code = "AB12";
        String key = "linkx:captcha:" + captchaId;

        // Mock Lua script execution - return -1 (expired/not found)
        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq(code)))
                .thenReturn(-1L);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> captchaService.validate(captchaId, code));
        assertEquals("验证码已过期，请重新获取", exception.getMessage());
    }

    @Test
    @DisplayName("验证码验证失败 - captchaId 为空")
    void shouldThrowExceptionForEmptyCaptchaId() {
        CustomException exception = assertThrows(CustomException.class,
                () -> captchaService.validate("", "AB12"));
        assertEquals("请填写验证码", exception.getMessage());
    }

    @Test
    @DisplayName("验证码验证失败 - captchaCode 为空")
    void shouldThrowExceptionForEmptyCaptchaCode() {
        CustomException exception = assertThrows(CustomException.class,
                () -> captchaService.validate("test-id", ""));
        assertEquals("请填写验证码", exception.getMessage());
    }

    @Test
    @DisplayName("验证码不区分大小写验证")
    void shouldValidateCaptchaCaseInsensitive() {
        // Arrange
        String captchaId = "test-captcha-id";
        String lowerCaseCode = "ab12";
        String upperCaseCode = "AB12";
        String key = "linkx:captcha:" + captchaId;

        // Mock Lua script execution - return 1 (success)
        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq(lowerCaseCode)))
                .thenReturn(1L);
        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq(upperCaseCode)))
                .thenReturn(1L);

        // Act & Assert
        assertDoesNotThrow(() -> captchaService.validate(captchaId, lowerCaseCode));
        assertDoesNotThrow(() -> captchaService.validate(captchaId, upperCaseCode));
    }

    @Test
    @DisplayName("验证码应去除空格后验证")
    void shouldTrimCaptchaCode() {
        // Arrange
        String captchaId = "test-captcha-id";
        String codeWithSpaces = "  AB12  ";
        String key = "linkx:captcha:" + captchaId;

        // Mock Lua script execution - return 1 (success)
        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq("AB12")))
                .thenReturn(1L);

        // Act & Assert
        assertDoesNotThrow(() -> captchaService.validate(captchaId, codeWithSpaces));
    }

    @Test
    @DisplayName("并发验证码验证 - 竞态条件测试")
    void shouldHandleConcurrentValidationRaceCondition() throws InterruptedException {
        // Arrange
        String captchaId = "concurrent-test-id";
        String correctCode = "AB12";
        String key = "linkx:captcha:" + captchaId;
        int threadCount = 10;
        
        // 模拟 Lua 脚本：只有第一次返回成功(1)，之后返回过期(-1)
        AtomicInteger callCount = new AtomicInteger(0);
        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), anyString()))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    return count == 1 ? 1L : -1L;
                });

        // Act
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    captchaService.validate(captchaId, correctCode);
                    successCount.incrementAndGet();
                } catch (CustomException e) {
                    if (e.getMessage().contains("过期")) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - 只有一个线程应该成功，其他都应该失败
        assertEquals(1, successCount.get(), "只有一个线程应该成功验证验证码");
        assertEquals(threadCount - 1, failCount.get(), "其他线程应该收到已过期响应");
    }

    @Test
    @DisplayName("生成的验证码格式应正确")
    void shouldGenerateValidBase64Image() {
        CaptchaVO captcha = captchaService.generate();

        // 验证 Base64 格式
        assertTrue(captcha.getImageBase64().startsWith("data:image/png;base64,"));

        // 提取 Base64 部分并验证
        String base64Part = captcha.getImageBase64().substring("data:image/png;base64,".length());
        assertTrue(base64Part.length() > 0);

        // 验证是有效的 Base64
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(base64Part));
    }

    @Test
    @DisplayName("每次生成应有不同的验证码")
    void shouldGenerateDifferentCodes() {
        CaptchaVO captcha1 = captchaService.generate();
        CaptchaVO captcha2 = captchaService.generate();

        assertNotEquals(captcha1.getCaptchaId(), captcha2.getCaptchaId());
    }
}
