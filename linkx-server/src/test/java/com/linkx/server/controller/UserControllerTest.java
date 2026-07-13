package com.linkx.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.controller.vo.UserProfileVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 单元测试
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("用户资料控制器测试")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SysUserService sysUserService;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private JwtUtils jwtUtils;

    private static final String TEST_TOKEN = "Bearer test-jwt-token";
    private static final Long TEST_USER_ID = 12345L;
    private static final String TEST_USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(TEST_USER_ID);
    }

    @Test
    @DisplayName("获取当前用户信息 - 已登录用户应成功")
    void shouldGetCurrentUserSuccessfully() throws Exception {
        // Arrange
        SysUser user = createTestUser();
        when(sysUserService.getById(TEST_USER_ID)).thenReturn(user);

        // Act & Assert
        mockMvc.perform(get("/user/me")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.data.nickname").value("Test User"))
                .andExpect(jsonPath("$.data.avatar").value("/avatar.png"));
    }

    @Test
    @DisplayName("获取当前用户信息 - 未登录应返回401")
    void shouldReturn401WhenNotLoggedIn() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    @DisplayName("获取当前用户信息 - 用户不存在应返回404")
    void shouldReturn404WhenUserNotFound() throws Exception {
        // Arrange
        when(sysUserService.getById(TEST_USER_ID)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/user/me")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("更新用户资料 - 有效数据应成功")
    void shouldUpdateProfileSuccessfully() throws Exception {
        // Arrange
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("New Nickname");
        dto.setSignature("New signature");

        SysUser updatedUser = createTestUser();
        updatedUser.setNickname("New Nickname");
        updatedUser.setSignature("New signature");

        when(sysUserService.updateProfile(eq(TEST_USER_ID), any(UpdateProfileDTO.class)))
                .thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(put("/user/profile")
                        .header("Authorization", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("New Nickname"))
                .andExpect(jsonPath("$.data.signature").value("New signature"));
    }

    @Test
    @DisplayName("更新用户资料 - 超长昵称应失败")
    void shouldFailWhenNicknameTooLong() throws Exception {
        // Arrange
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("a".repeat(51)); // 超过50字符限制

        // Act & Assert
        mockMvc.perform(put("/user/profile")
                        .header("Authorization", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("上传头像 - 有效的图片文件应成功")
    void shouldUploadAvatarSuccessfully() throws Exception {
        // Arrange
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "test image content".getBytes()
        );

        String expectedUrl = "http://localhost:9000/linkx/avatar/12345/1234567890.png";
        when(fileStorageService.uploadFile(any(), anyString())).thenReturn(expectedUrl);

        // Act & Assert
        mockMvc.perform(multipart("/user/avatar")
                        .file(avatarFile)
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(expectedUrl));

        // Verify service calls
        verify(fileStorageService).uploadFile(any(), contains("avatar/" + TEST_USER_ID));
        verify(sysUserService).updateAvatar(eq(TEST_USER_ID), eq(expectedUrl));
    }

    @Test
    @DisplayName("上传头像 - 非图片文件应失败")
    void shouldFailForNonImageFile() throws Exception {
        // Arrange
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "text content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/user/avatar")
                        .file(textFile)
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只支持图片文件"));
    }

    @Test
    @DisplayName("上传头像 - 未登录应返回401")
    void shouldReturn401WhenUploadingAvatarWithoutLogin() throws Exception {
        // Arrange
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "test image content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/user/avatar")
                        .file(avatarFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    @DisplayName("获取用户公开资料 - 存在的用户应成功")
    void shouldGetUserProfileSuccessfully() throws Exception {
        // Arrange
        Long targetUserId = 67890L;
        SysUser user = createTestUser();
        user.setId(targetUserId);
        user.setUsername("targetuser");

        when(sysUserService.getById(targetUserId)).thenReturn(user);

        // Act & Assert
        mockMvc.perform(get("/user/{userId}/profile", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("targetuser"))
                .andExpect(jsonPath("$.data.id").value(targetUserId));
    }

    @Test
    @DisplayName("获取用户公开资料 - 不存在的用户应返回404")
    void shouldReturn404WhenTargetUserNotFound() throws Exception {
        // Arrange
        Long targetUserId = 99999L;
        when(sysUserService.getById(targetUserId)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/user/{userId}/profile", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    private SysUser createTestUser() {
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setNickname("Test User");
        user.setAvatar("/avatar.png");
        user.setSignature("Test signature");
        return user;
    }
}
