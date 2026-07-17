package com.linkx.server.service;

import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenService Token服务测试
 */
@DisplayName("TokenService Token服务测试")
class TokenServiceTest extends BaseIntegrationTest {

    @Autowired
    private TokenService tokenService;

    @Nested
    @DisplayName("issueTokenPair 生成令牌对测试")
    class IssueTokenPairTests {

        @Test
        @DisplayName("生成Token应成功")
        void issueTokenPair_success() {
            SysUser user = SysUser.builder()
                    .id(123L)
                    .username("testuser")
                    .build();

            TokenVO vo = tokenService.issueTokenPair(user);

            assertNotNull(vo);
            assertNotNull(vo.getAccessToken());
            assertNotNull(vo.getRefreshToken());
            assertFalse(vo.getAccessToken().isEmpty());
            assertFalse(vo.getRefreshToken().isEmpty());
        }

        @Test
        @DisplayName("accessToken和refreshToken应不同")
        void accessAndRefreshDifferent() {
            SysUser user = SysUser.builder()
                    .id(123L)
                    .username("testuser")
                    .build();

            TokenVO vo = tokenService.issueTokenPair(user);

            assertNotEquals(vo.getAccessToken(), vo.getRefreshToken());
        }

        @Test
        @DisplayName("应包含用户信息")
        void containsUserInfo() {
            SysUser user = SysUser.builder()
                    .id(456L)
                    .username("tokenuser")
                    .nickname("Token用户")
                    .build();

            TokenVO vo = tokenService.issueTokenPair(user);

            assertNotNull(vo.getUser());
            assertEquals(456L, vo.getUser().getId());
            assertEquals("tokenuser", vo.getUser().getUsername());
        }
    }

    @Nested
    @DisplayName("refreshAccessToken 刷新令牌测试")
    class RefreshTokenTests {

        @Test
        @DisplayName("使用无效refreshToken应抛出异常")
        void invalidRefreshToken_throws() {
            assertThrows(com.linkx.server.exception.CustomException.class,
                    () -> tokenService.refreshAccessToken("invalid.refresh.token"));
        }

        @Test
        @DisplayName("使用空refreshToken应抛出异常")
        void emptyRefreshToken_throws() {
            assertThrows(com.linkx.server.exception.CustomException.class,
                    () -> tokenService.refreshAccessToken(""));
        }
    }

    @Nested
    @DisplayName("logout 登出测试")
    class LogoutTests {

        @Test
        @DisplayName("登出应不抛异常")
        void logout_noException() {
            SysUser user = SysUser.builder()
                    .id(111L)
                    .username("logoutuser")
                    .build();
            TokenVO vo = tokenService.issueTokenPair(user);

            assertDoesNotThrow(() -> tokenService.logout(vo.getAccessToken(), vo.getRefreshToken()));
        }
    }

    @Nested
    @DisplayName("assertAccessTokenActive 测试")
    class AssertAccessTokenActiveTests {

        @Test
        @DisplayName("有效Token应不抛异常")
        void validToken_noException() {
            SysUser user = SysUser.builder()
                    .id(222L)
                    .username("activeuser")
                    .build();
            TokenVO vo = tokenService.issueTokenPair(user);

            assertDoesNotThrow(() -> tokenService.assertAccessTokenActive(vo.getAccessToken()));
        }

        @Test
        @DisplayName("无效Token应抛出异常")
        void invalidToken_throws() {
            assertThrows(com.linkx.server.exception.CustomException.class,
                    () -> tokenService.assertAccessTokenActive("invalid.token.here"));
        }
    }
}
