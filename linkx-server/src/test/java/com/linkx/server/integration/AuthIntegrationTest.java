package com.linkx.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("认证集成测试")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "TestPassword123!";
    private static final String TEST_NICKNAME = "Test User";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(redisTemplate.keys("linkx:*"));
    }

    @Test
    @DisplayName("完整流程：注册->登录->刷新Token->登出")
    void shouldCompleteFullAuthFlow() throws Exception {
        // 注册
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(TEST_USERNAME);
        registerDTO.setPassword(TEST_PASSWORD);
        registerDTO.setNickname(TEST_NICKNAME);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证用户已保存到数据库（通过登录是否成功来验证）
        // SysUserMapper 可能没有 selectOneByUsername 方法

        // 登录
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(TEST_USERNAME);
        loginDTO.setPassword(TEST_PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseContent)
                .get("data").get("accessToken").asText();
        String refreshToken = objectMapper.readTree(responseContent)
                .get("data").get("refreshToken").asText();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        // 刷新Token
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 登出
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("登录失败：密码错误")
    void shouldFailWhenPasswordWrong() throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(TEST_USERNAME);
        registerDTO.setPassword(TEST_PASSWORD);
        registerDTO.setNickname(TEST_NICKNAME);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)));

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(TEST_USERNAME);
        loginDTO.setPassword("WrongPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
