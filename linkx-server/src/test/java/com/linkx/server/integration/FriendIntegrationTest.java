package com.linkx.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("好友系统集成测试")
class FriendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String USER_A = "frienduser_a";
    private static final String USER_B = "frienduser_b";
    private static final String PASSWORD = "TestPassword123!";

    @BeforeEach
    void setUp() {
        try {
            var keys = redisTemplate.keys("linkx:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
            // 本地未启动 Redis 时跳过清理
        }
    }

    @Test
    @DisplayName("完整流程：申请好友 -> 拒绝 -> 反向申请 -> 对方收到待处理")
    void shouldRejectThenAllowReverseRequest() throws Exception {
        String tokenA = registerAndLogin(USER_A, "User A");
        String tokenB = registerAndLogin(USER_B, "User B");

        SendFriendRequestDTO requestDTO = new SendFriendRequestDTO();
        requestDTO.setUsername(USER_B);
        requestDTO.setMessage("你好，加个好友");

        mockMvc.perform(post("/friend/request")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult incomingResult = mockMvc.perform(get("/friend/requests/incoming")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value(0))
                .andReturn();

        String incomingJson = incomingResult.getResponse().getContentAsString();
        Long requestId = Long.parseLong(objectMapper.readTree(incomingJson).get("data").get(0).get("id").asText());

        mockMvc.perform(post("/friend/requests/" + requestId + "/reject")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/friend/requests/incoming")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value(2));

        SendFriendRequestDTO reverseDTO = new SendFriendRequestDTO();
        reverseDTO.setUsername(USER_A);
        reverseDTO.setMessage("我也加你");

        mockMvc.perform(post("/friend/request")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reverseDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/friend/requests/incoming")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value(0))
                .andExpect(jsonPath("$.data[0].fromUsername").value(USER_B));

        mockMvc.perform(get("/friend/requests/outgoing")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value(2));
    }

    @Test
    @DisplayName("完整流程：申请好友 -> 同意 -> 双方好友列表可见")
    void shouldCompleteFriendFlow() throws Exception {
        String tokenA = registerAndLogin(USER_A, "User A");
        String tokenB = registerAndLogin(USER_B, "User B");

        SendFriendRequestDTO requestDTO = new SendFriendRequestDTO();
        requestDTO.setUsername(USER_B);
        requestDTO.setMessage("你好，加个好友");

        mockMvc.perform(post("/friend/request")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult incomingResult = mockMvc.perform(get("/friend/requests/incoming")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value(0))
                .andReturn();

        String incomingJson = incomingResult.getResponse().getContentAsString();
        Long requestId = Long.parseLong(objectMapper.readTree(incomingJson).get("data").get(0).get("id").asText());

        mockMvc.perform(post("/friend/requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/friend/list")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value(USER_B));

        mockMvc.perform(get("/friend/list")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value(USER_A));
    }

    private String registerAndLogin(String username, String nickname) throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(username);
        registerDTO.setPassword(PASSWORD);
        registerDTO.setNickname(nickname);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }
}
