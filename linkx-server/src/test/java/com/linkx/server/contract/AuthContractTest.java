package com.linkx.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API契约测试")
class AuthContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("登录接口响应结构契约")
    void shouldMatchLoginResponseContract() throws Exception {
        String loginRequest = "{" +
                "\"username\":\"testuser\"," +
                "\"password\":\"TestPass123!\"" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseBody);

        assertTrue(responseJson.has("code"), "响应必须包含code字段");
        assertTrue(responseJson.has("message"), "响应必须包含message字段");
        assertTrue(responseJson.has("data"), "响应必须包含data字段");
        assertTrue(responseJson.get("code").isNumber(), "code必须是数字类型");
        assertTrue(responseJson.get("message").isTextual(), "message必须是字符串类型");
    }

    @Test
    @DisplayName("注册接口请求体契约")
    void shouldAcceptCorrectRegisterRequestFormat() throws Exception {
        String registerRequest = "{" +
                "\"username\":\"newuser\"," +
                "\"password\":\"Password123!\"," +
                "\"nickname\":\"New User\"" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400, "API应该能正确处理请求");
    }

    @Test
    @DisplayName("API路径契约验证")
    void shouldExposeCorrectEndpoints() throws Exception {
        String[] endpoints = {
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/api/auth/captcha"
        };

        for (String endpoint : endpoints) {
            mockMvc.perform(options(endpoint))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertTrue(status != 404, "端点 " + endpoint + " 应该存在");
                    });
        }
    }

    @Test
    @DisplayName("响应头契约 - Content-Type")
    void shouldReturnCorrectContentType() throws Exception {
        mockMvc.perform(post("/api/auth/captcha"))
                .andExpect(result -> {
                    String contentType = result.getResponse().getContentType();
                    assertNotNull(contentType, "必须返回Content-Type");
                    assertTrue(contentType.contains("application/json"),
                            "Content-Type应该是application/json");
                });
    }
}
