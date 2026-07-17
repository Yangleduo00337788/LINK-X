package com.linkx.server.service;

import com.linkx.server.controller.vo.CaptchaVO;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaptchaService 验证码服务测试
 */
@DisplayName("CaptchaService 验证码服务测试")
class CaptchaServiceTest extends BaseIntegrationTest {

    @Autowired
    private CaptchaService captchaService;

    @Nested
    @DisplayName("generate 生成验证码测试")
    class GenerateTests {

        @Test
        @DisplayName("生成验证码应成功")
        void generate_success() {
            CaptchaVO captcha = captchaService.generate();

            assertNotNull(captcha);
            assertNotNull(captcha.getCaptchaId());
            assertFalse(captcha.getCaptchaId().isEmpty());
            assertNotNull(captcha.getImageBase64());
            assertTrue(captcha.getImageBase64().startsWith("data:image/png;base64,"));
            assertEquals(300, captcha.getExpireSeconds()); // 5分钟
        }

        @Test
        @DisplayName("每次生成应返回不同的验证码ID")
        void generate_uniqueIds() {
            CaptchaVO captcha1 = captchaService.generate();
            CaptchaVO captcha2 = captchaService.generate();

            assertNotEquals(captcha1.getCaptchaId(), captcha2.getCaptchaId());
        }

        @Test
        @DisplayName("验证码图片应有效")
        void generate_validImage() {
            CaptchaVO captcha = captchaService.generate();
            String base64 = captcha.getImageBase64();
            assertTrue(base64.length() > 100, "验证码图片应该足够长");
        }
    }

    @Nested
    @DisplayName("validate 验证验证码测试")
    class ValidateTests {

        @Test
        @DisplayName("空captchaId应抛出异常")
        void emptyCaptchaId_throws() {
            assertThrows(com.linkx.server.exception.CustomException.class,
                    () -> captchaService.validate("", "1234"));
        }

        @Test
        @DisplayName("空验证码应抛出异常")
        void emptyCode_throws() {
            CaptchaVO captcha = captchaService.generate();
            assertThrows(com.linkx.server.exception.CustomException.class,
                    () -> captchaService.validate(captcha.getCaptchaId(), ""));
        }

        @Test
        @DisplayName("不存在的验证码应抛出异常")
        void nonExistentCaptcha_throws() {
            assertThrows(com.linkx.server.exception.CustomException.class,
                    () -> captchaService.validate("non-existent-id", "1234"));
        }
    }
}
