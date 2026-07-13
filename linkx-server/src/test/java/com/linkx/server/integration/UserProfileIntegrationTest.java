package com.linkx.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("用户资料集成测试")
class UserProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TEST_USERNAME = "profiletest";
    private static final String TEST_PASSWORD = "TestPassword123!";
    private static final String TEST_NICKNAME = "Profile Test User";

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        // 清理 Redis
        redisTemplate.delete(redisTemplate.keys("linkx:*"));

        // 注册用户
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(TEST_USERNAME);
        registerDTO.setPassword(TEST_PASSWORD);
        registerDTO.setNickname(TEST_NICKNAME);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk());

        // 登录获取 Token
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(TEST_USERNAME);
        loginDTO.setPassword(TEST_PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        accessToken = objectMapper.readTree(responseContent)
                .get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("获取当前用户信息 - 登录后应返回用户信息")
    void shouldGetCurrentUserAfterLogin() throws Exception {
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.data.nickname").value(TEST_NICKNAME))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @DisplayName("更新用户资料 - 更新昵称应成功")
    void shouldUpdateNicknameSuccessfully() throws Exception {
        // Arrange
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("Updated Nickname");

        // Act & Assert
        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("Updated Nickname"));

        // Verify by getting user again
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Updated Nickname"));
    }

    @Test
    @DisplayName("更新用户资料 - 更新签名应成功")
    void shouldUpdateSignatureSuccessfully() throws Exception {
        // Arrange
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setSignature("This is my new signature!");

        // Act & Assert
        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.signature").value("This is my new signature!"));
    }

    @Test
    @DisplayName("更新用户资料 - 同时更新昵称和签名")
    void shouldUpdateBothNicknameAndSignature() throws Exception {
        // Arrange
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("New Nick");
        dto.setSignature("New Signature");

        // Act & Assert
        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("New Nick"))
                .andExpect(jsonPath("$.data.signature").value("New Signature"));
    }

    @Test
    @DisplayName("上传头像 - 有效的图片文件应成功")
    void shouldUploadAvatarSuccessfully() throws Exception {
        // Arrange
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file",
                "test-avatar.png",
                "image/png",
                createFakePngContent()
        );

        // Act & Assert
        MvcResult result = mockMvc.perform(multipart("/api/user/avatar")
                        .file(avatarFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        // Verify the avatar URL is returned and saved
        String responseContent = result.getResponse().getContentAsString();
        String avatarUrl = objectMapper.readTree(responseContent).get("data").asText();
        assertTrue(avatarUrl.contains("avatar"));

        // Verify user has new avatar
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar").value(avatarUrl));
    }

    @Test
    @DisplayName("上传头像 - 非图片文件应失败")
    void shouldFailWhenUploadingNonImageFile() throws Exception {
        // Arrange
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "This is not an image".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/user/avatar")
                        .file(textFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只支持图片文件"));
    }

    @Test
    @DisplayName("获取用户公开资料 - 应返回用户公开信息")
    void shouldGetUserPublicProfile() throws Exception {
        // Arrange - Get current user to get userId
        MvcResult userResult = mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = userResult.getResponse().getContentAsString();
        Long userId = objectMapper.readTree(responseContent).get("data").get("id").asLong();

        // Act & Assert - Get public profile
        mockMvc.perform(get("/api/user/{userId}/profile", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME));
    }

    @Test
    @DisplayName("完整流程：注册->登录->更新资料->上传头像->获取信息")
    void shouldCompleteFullProfileFlow() throws Exception {
        // 1. 更新昵称
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("Flow Test Nickname");
        dto.setSignature("Flow Test Signature");

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // 2. 上传头像
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file",
                "flow-avatar.png",
                "image/png",
                createFakePngContent()
        );

        MvcResult avatarResult = mockMvc.perform(multipart("/api/user/avatar")
                        .file(avatarFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        String avatarUrl = objectMapper.readTree(
                avatarResult.getResponse().getContentAsString()).get("data").asText();

        // 3. 获取用户信息并验证所有更改
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Flow Test Nickname"))
                .andExpect(jsonPath("$.data.signature").value("Flow Test Signature"))
                .andExpect(jsonPath("$.data.avatar").value(avatarUrl));
    }

    private byte[] createFakePngContent() {
        // 最小的 PNG 文件头部
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
    }
}
