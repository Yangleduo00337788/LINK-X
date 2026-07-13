package com.linkx.server.service;

import com.linkx.server.controller.dto.UpdateProfileDTO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户资料服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户资料服务测试")
class SysUserProfileServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private SysUserServiceImpl sysUserService;

    private static final Long TEST_USER_ID = 12345L;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_NICKNAME = "Test User";
    private static final String TEST_AVATAR = "/default-avatar.svg";

    @Test
    @DisplayName("更新用户资料 - 更新昵称应成功")
    void shouldUpdateNicknameSuccessfully() {
        // Arrange
        SysUser existingUser = createTestUser();
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);
        when(sysUserMapper.update(any(SysUser.class))).thenReturn(1);

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("New Nickname");

        // Act
        SysUser result = sysUserService.updateProfile(TEST_USER_ID, dto);

        // Assert
        assertNotNull(result);
        assertEquals("New Nickname", result.getNickname());
        assertEquals(existingUser.getSignature(), result.getSignature()); // 签名未变
        verify(sysUserMapper).update(any(SysUser.class));
    }

    @Test
    @DisplayName("更新用户资料 - 更新签名应成功")
    void shouldUpdateSignatureSuccessfully() {
        // Arrange
        SysUser existingUser = createTestUser();
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);
        when(sysUserMapper.update(any(SysUser.class))).thenReturn(1);

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setSignature("New signature for testing");

        // Act
        SysUser result = sysUserService.updateProfile(TEST_USER_ID, dto);

        // Assert
        assertNotNull(result);
        assertEquals("New signature for testing", result.getSignature());
        assertEquals(existingUser.getNickname(), result.getNickname()); // 昵称未变
    }

    @Test
    @DisplayName("更新用户资料 - 同时更新昵称和签名应成功")
    void shouldUpdateBothNicknameAndSignature() {
        // Arrange
        SysUser existingUser = createTestUser();
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);
        when(sysUserMapper.update(any(SysUser.class))).thenReturn(1);

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("Updated Nickname");
        dto.setSignature("Updated signature");

        // Act
        SysUser result = sysUserService.updateProfile(TEST_USER_ID, dto);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Nickname", result.getNickname());
        assertEquals("Updated signature", result.getSignature());
    }

    @Test
    @DisplayName("更新用户资料 - 用户不存在应抛出异常")
    void shouldThrowExceptionWhenUserNotFound() {
        // Arrange
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(null);

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("New Nickname");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> sysUserService.updateProfile(TEST_USER_ID, dto));
        assertEquals(404, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    @DisplayName("更新用户资料 - 空昵称不应更新")
    void shouldNotUpdateWhenNicknameIsEmpty() {
        // Arrange
        SysUser existingUser = createTestUser();
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("");
        dto.setSignature("New signature");

        // Act
        SysUser result = sysUserService.updateProfile(TEST_USER_ID, dto);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_NICKNAME, result.getNickname()); // 昵称未变
        assertEquals("New signature", result.getSignature()); // 签名已更新
    }

    @Test
    @DisplayName("更新用户资料 - null昵称不应更新")
    void shouldNotUpdateWhenNicknameIsNull() {
        // Arrange
        SysUser existingUser = createTestUser();
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname(null);
        dto.setSignature("New signature");

        // Act
        SysUser result = sysUserService.updateProfile(TEST_USER_ID, dto);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_NICKNAME, result.getNickname()); // 昵称未变
        assertEquals("New signature", result.getSignature()); // 签名已更新
    }

    @Test
    @DisplayName("更新头像 - 应成功并更新头像URL")
    void shouldUpdateAvatarSuccessfully() {
        // Arrange
        SysUser existingUser = createTestUser();
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);
        when(sysUserMapper.update(any(SysUser.class))).thenReturn(1);

        String newAvatarUrl = "http://localhost:9000/linkx/avatar/123/1234567890.png";

        // Act
        sysUserService.updateAvatar(TEST_USER_ID, newAvatarUrl);

        // Assert
        verify(sysUserMapper).update(argThat(user -> 
            user.getAvatar().equals(newAvatarUrl)
        ));
    }

    @Test
    @DisplayName("更新头像 - 用户不存在应抛出异常")
    void shouldThrowExceptionWhenUpdatingAvatarForNonExistentUser() {
        // Arrange
        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(null);

        String newAvatarUrl = "http://localhost:9000/linkx/avatar/123/1234567890.png";

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> sysUserService.updateAvatar(TEST_USER_ID, newAvatarUrl));
        assertEquals(404, exception.getCode());
    }

    @Test
    @DisplayName("更新头像 - 应删除旧头像")
    void shouldDeleteOldAvatarWhenUpdating() {
        // Arrange
        String oldAvatarUrl = "http://localhost:9000/linkx/avatar/old/1234567890.png";
        SysUser existingUser = createTestUser();
        existingUser.setAvatar(oldAvatarUrl);

        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);
        when(sysUserMapper.update(any(SysUser.class))).thenReturn(1);

        String newAvatarUrl = "http://localhost:9000/linkx/avatar/new/1234567890.png";

        // Act
        sysUserService.updateAvatar(TEST_USER_ID, newAvatarUrl);

        // Assert
        verify(fileStorageService).deleteFile(oldAvatarUrl);
    }

    @Test
    @DisplayName("更新头像 - 不应删除默认头像")
    void shouldNotDeleteDefaultAvatar() {
        // Arrange
        SysUser existingUser = createTestUser();
        // existingUser.getAvatar() returns "/default-avatar.svg"

        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);
        when(sysUserMapper.update(any(SysUser.class))).thenReturn(1);

        String newAvatarUrl = "http://localhost:9000/linkx/avatar/123/1234567890.png";

        // Act
        sysUserService.updateAvatar(TEST_USER_ID, newAvatarUrl);

        // Assert
        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("更新头像 - 删除旧头像失败不应影响新头像更新")
    void shouldUpdateAvatarEvenWhenDeletingOldFails() {
        // Arrange
        String oldAvatarUrl = "http://localhost:9000/linkx/avatar/old/1234567890.png";
        SysUser existingUser = createTestUser();
        existingUser.setAvatar(oldAvatarUrl);

        when(sysUserMapper.selectOneById(TEST_USER_ID)).thenReturn(existingUser);
        when(sysUserMapper.update(any(SysUser.class))).thenReturn(1);
        doThrow(new RuntimeException("MinIO error")).when(fileStorageService).deleteFile(oldAvatarUrl);

        String newAvatarUrl = "http://localhost:9000/linkx/avatar/new/1234567890.png";

        // Act & Assert
        assertDoesNotThrow(() -> sysUserService.updateAvatar(TEST_USER_ID, newAvatarUrl));
        verify(sysUserMapper).update(argThat(user -> 
            user.getAvatar().equals(newAvatarUrl)
        ));
    }

    private SysUser createTestUser() {
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setNickname(TEST_NICKNAME);
        user.setAvatar(TEST_AVATAR);
        user.setSignature("Original signature");
        user.setStatus(1);
        return user;
    }
}
