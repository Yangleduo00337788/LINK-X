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
            String body = """
                {
                    "username": "%s",
                    "password": "Test1234abcd",
                    "nickname": "测试用户",
                    "email": "%s"
                }
                """.formatted(username, username + "@linkx.test");

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
            String body = """
                {
                    "username": "%s",
                    "password": "%s",
                    "nickname": "重复测试",
                    "email": "%s"
                }
                """.formatted(username, password, username + "@linkx.test");

            // 第一次注册成功
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200));

            // 第二次注册失败
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
                    "email": "shortuser@linkx.test"
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
}
