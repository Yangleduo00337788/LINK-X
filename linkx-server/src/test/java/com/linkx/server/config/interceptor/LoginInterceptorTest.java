package com.linkx.server.config.interceptor;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LoginInterceptor 登录拦截器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginInterceptor 登录拦截器测试")
class LoginInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TokenService tokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private LoginInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LoginInterceptor(jwtUtils, tokenService);
    }

    @Nested
    @DisplayName("preHandle 登录验证测试")
    class PreHandleTests {

        @Test
        @DisplayName("OPTIONS请求应直接放行")
        void optionsRequest_shouldPass() {
            when(request.getMethod()).thenReturn("OPTIONS");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertTrue(result);
            verify(jwtUtils, never()).getTokenType(any());
        }

        @Test
        @DisplayName("无Authorization头应抛出401")
        void noAuthHeader_shouldThrow401() {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn(null);

            CustomException ex = assertThrows(CustomException.class,
                    () -> interceptor.preHandle(request, response, new Object()));

            assertEquals(401, ex.getCode());
            assertEquals("未登录或登录已过期", ex.getMessage());
        }

        @Test
        @DisplayName("空Authorization头应抛出401")
        void emptyAuthHeader_shouldThrow401() {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("");

            CustomException ex = assertThrows(CustomException.class,
                    () -> interceptor.preHandle(request, response, new Object()));

            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("Bearer Token应正确解析")
        void bearerToken_shouldBeParsed() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
            when(jwtUtils.getTokenType("valid-token")).thenReturn(TokenType.ACCESS);
            doNothing().when(tokenService).assertAccessTokenActive("valid-token");
            when(jwtUtils.getUserIdFromToken("valid-token")).thenReturn(123L);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertTrue(result);
            verify(request).setAttribute("userId", 123L);
        }

        @Test
        @DisplayName("无Bearer前缀Token应正常处理")
        void tokenWithoutBearer_shouldWork() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("raw-token");
            when(jwtUtils.getTokenType("raw-token")).thenReturn(TokenType.ACCESS);
            doNothing().when(tokenService).assertAccessTokenActive("raw-token");
            when(jwtUtils.getUserIdFromToken("raw-token")).thenReturn(456L);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertTrue(result);
            verify(request).setAttribute("userId", 456L);
        }

        @Test
        @DisplayName("Refresh Token应被拒绝")
        void refreshToken_shouldBeRejected() {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer refresh-token");
            when(jwtUtils.getTokenType("refresh-token")).thenReturn(TokenType.REFRESH);

            CustomException ex = assertThrows(CustomException.class,
                    () -> interceptor.preHandle(request, response, new Object()));

            assertEquals(401, ex.getCode());
            assertEquals("无效的访问令牌", ex.getMessage());
        }

        @Test
        @DisplayName("无效Token应抛出401")
        void invalidToken_shouldThrow401() {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
            when(jwtUtils.getTokenType("bad-token")).thenReturn(TokenType.ACCESS);
            doThrow(new RuntimeException("Token invalid"))
                    .when(tokenService).assertAccessTokenActive("bad-token");

            CustomException ex = assertThrows(CustomException.class,
                    () -> interceptor.preHandle(request, response, new Object()));

            assertEquals(401, ex.getCode());
            assertEquals("登录已过期，请重新登录", ex.getMessage());
        }

        @Test
        @DisplayName("JwtUtils异常应转为401")
        void jwtUtilsException_shouldThrow401() {
            when(request.getMethod()).thenReturn("GET");
            when(request.getHeader("Authorization")).thenReturn("Bearer token");
            when(jwtUtils.getTokenType("token")).thenThrow(new RuntimeException("Parse error"));

            CustomException ex = assertThrows(CustomException.class,
                    () -> interceptor.preHandle(request, response, new Object()));

            assertEquals(401, ex.getCode());
        }
    }
}
