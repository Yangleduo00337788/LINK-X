package com.linkx.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 认证控制器集成测试
 */
@DisplayName("AuthController 认证控制器集成测试")
class AuthControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /auth/register 注册接口测试")
    class RegisterTests {

        @Test
        @DisplayName("正常注册应返回code=200")
        void normalRegister_returnsOk() throws Exception {
            String username = "testuser" + System.nanoTime();
            String email = username + "@linkx.test";
            String emailCode = seedRegisterEmailCode(email);
            String body = """
                {
                    "username": "%s",
                    "password": "Test1234abcd",
                    "nickname": "测试用户",
                    "email": "%s",
                    "emailCode": "%s"
                }
                """.formatted(username, email, emailCode);

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("success"));
        }

        @Test
        @DisplayName("重复注册应返回code=400")
        void duplicateRegister_returnsError() throws Exception {
            String username = "dupuser" + System.nanoTime();
            String password = "Test1234abcd";
            register(username, password, "重复测试");

            // 换一个邮箱拿到验证码，但用户名重复应失败
            String email2 = username + "b@linkx.test";
            String emailCode = seedRegisterEmailCode(email2);
            String body = """
                {
                    "username": "%s",
                    "password": "%s",
                    "nickname": "重复测试",
                    "email": "%s",
                    "emailCode": "%s"
                }
                """.formatted(username, password, email2, emailCode);

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("用户名过短应返回code=400")
        void shortUsername_returnsError() throws Exception {
            String body = """
                {
                    "username": "ab",
                    "password": "Test1234abcd",
                    "nickname": "短用户",
                    "email": "shortuser@linkx.test",
                    "emailCode": "123456"
                }
                """;

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Nested
    @DisplayName("POST /auth/login 登录接口测试")
    class LoginTests {

        @Test
        @DisplayName("正常登录应返回Token")
        void normalLogin_returnsToken() throws Exception {
            // 先注册
            String username = "loginuser" + System.nanoTime();
            String password = "Test1234abcd";
            register(username, password, "登录用户");

            // 再登录
            String body = """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(username, password);

            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").exists())
                    .andExpect(jsonPath("$.data.refreshToken").exists())
                    .andExpect(jsonPath("$.data.user").exists())
                    .andReturn();

            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            String accessToken = json.get("data").get("accessToken").asText();
            assertNotNull(accessToken);
            assertFalse(accessToken.isEmpty());
        }

        @Test
        @DisplayName("错误密码应返回错误码")
        void wrongPassword_returnsError() throws Exception {
            // 先注册
            String username = "wrongpwuser" + System.nanoTime();
            register(username, "Correct123", "错误密码测试");

            // 用错误密码登录
            String body = """
                {
                    "username": "%s",
                    "password": "WrongPassword"
                }
                """.formatted(username);

            // 登录失败返回400或401
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(400),
                            org.hamcrest.Matchers.is(401))));
        }
    }

    @Nested
    @DisplayName("GET /auth/captcha 验证码接口测试")
    class CaptchaTests {

        @Test
        @DisplayName("获取验证码应成功")
        void getCaptcha_returnsOk() throws Exception {
            mockMvc.perform(get("/auth/captcha"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.captchaId").exists())
                    .andExpect(jsonPath("$.data.imageBase64").exists());
        }
    }

    @Nested
    @DisplayName("GET /auth/config 鉴权配置接口测试")
    class ConfigTests {

        @Test
        @DisplayName("匿名获取配置应成功")
        void getConfig_returnsOk() throws Exception {
            mockMvc.perform(get("/auth/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.captchaEnabled").exists())
                    .andExpect(jsonPath("$.data.registerEnabled").exists())
                    .andExpect(jsonPath("$.data.passwordPolicy.minLength").exists());
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh 刷新令牌接口测试")
    class RefreshTests {

        @Test
        @DisplayName("有效 refreshToken 应返回新 accessToken")
        void refreshWithValidToken_returnsNewAccessToken() throws Exception {
            String username = "refreshuser" + System.nanoTime();
            String password = "Test1234abcd";
            register(username, password, "刷新测试");

            String loginBody = """
                    {
                        "username": "%s",
                        "password": "%s"
                    }
                    """.formatted(username, password);

            MvcResult loginResult = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();

            JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            String refreshToken = loginJson.get("data").get("refreshToken").asText();

            String refreshBody = """
                    {"refreshToken":"%s"}
                    """.formatted(refreshToken);

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").exists())
                    .andExpect(jsonPath("$.data.refreshToken").exists());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout 登出接口测试")
    class LogoutTests {

        @Test
        @DisplayName("登出后 accessToken 应失效")
        void logout_invalidatesToken() throws Exception {
            String username = "logoutuser" + System.nanoTime();
            String password = "Test1234abcd";
            register(username, password, "登出测试");

            String loginBody = """
                    {
                        "username": "%s",
                        "password": "%s"
                    }
                    """.formatted(username, password);

            MvcResult loginResult = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();

            JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            JsonNode data = loginJson.get("data");
            String accessToken = data.get("accessToken").asText();
            String refreshToken = data.get("refreshToken").asText();

            String logoutBody = """
                    {"refreshToken":"%s"}
                    """.formatted(refreshToken);

            mockMvc.perform(post("/auth/logout")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(logoutBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/user/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST /auth/send-register-code 发送注册验证码测试")
    class SendRegisterCodeTests {

        @Test
        @DisplayName("发送注册验证码应成功")
        void sendRegisterCode_success() throws Exception {
            String username = "regcode" + System.nanoTime();
            String email = username + "@linkx.test";
            mockMvc.perform(post("/auth/send-register-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","username":"%s"}
                                    """.formatted(email, username)))
                    .andExpect(jsonPath("$.code").value(200));

            String code = stringRedisTemplate.opsForValue().get("linkx:register-email:" + email.toLowerCase());
            assertNotNull(code);
            assertEquals(6, code.length());
        }
    }

    @Nested
    @DisplayName("POST /auth/send-reset-code 发送重置验证码测试")
    class SendResetCodeTests {

        @Test
        @DisplayName("已注册用户发送重置码应成功")
        void sendResetCode_success() throws Exception {
            String username = "rstcode" + (System.nanoTime() % 1_000_000_000L);
            if (username.length() > 32) {
                username = username.substring(0, 32);
            }
            register(username, "Test1234abcd", "重置码");

            mockMvc.perform(post("/auth/send-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s"}
                                    """.formatted(username)))
                    .andExpect(jsonPath("$.code").value(200));

            String code = stringRedisTemplate.opsForValue().get("linkx:reset-email:" + username);
            assertNotNull(code);
        }
    }

    @Nested
    @DisplayName("POST /auth/verify-reset-code 校验重置验证码测试")
    class VerifyResetCodeTests {

        @Test
        @DisplayName("正确验证码应校验通过")
        void verifyResetCode_success() throws Exception {
            String username = "vrfcode" + (System.nanoTime() % 1_000_000_000L);
            if (username.length() > 32) {
                username = username.substring(0, 32);
            }
            register(username, "Test1234abcd", "校验码");
            stringRedisTemplate.opsForValue().set("linkx:reset-email:" + username, "654321");

            mockMvc.perform(post("/auth/verify-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","code":"654321"}
                                    """.formatted(username)))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("错误验证码应返回400")
        void verifyResetCode_wrongCode() throws Exception {
            String username = "vrfbad" + (System.nanoTime() % 1_000_000_000L);
            if (username.length() > 32) {
                username = username.substring(0, 32);
            }
            register(username, "Test1234abcd", "校验失败");
            stringRedisTemplate.opsForValue().set("linkx:reset-email:" + username, "654321");

            mockMvc.perform(post("/auth/verify-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","code":"000000"}
                                    """.formatted(username)))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Nested
    @DisplayName("POST /auth/reset-password-captcha 重置密码验证码测试")
    class ResetPasswordCaptchaTests {

        @Test
        @DisplayName("已登录用户应能获取专用验证码")
        void resetPasswordCaptcha_success() throws Exception {
            TestUser user = registerAndLogin("rstcap");

            mockMvc.perform(post("/auth/reset-password-captcha")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.captchaId").exists())
                    .andExpect(jsonPath("$.data.imageBase64").exists());
        }

        @Test
        @DisplayName("未登录应返回401")
        void resetPasswordCaptcha_unauthorized() throws Exception {
            mockMvc.perform(post("/auth/reset-password-captcha"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST /auth/reset-password 登录态重置密码测试")
    class ResetPasswordTests {

        @Test
        @DisplayName("已登录用户重置密码应成功")
        void resetPassword_success() throws Exception {
            TestUser user = registerAndLogin("rstpwd");

            mockMvc.perform(post("/auth/reset-password")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"newPassword":"ResetPass1234"}
                                    """))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("未登录重置密码应返回401")
        void resetPassword_unauthorized() throws Exception {
            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"newPassword":"ResetPass1234"}
                                    """))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
