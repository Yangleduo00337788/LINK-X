package com.linkx.server.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtils 单元测试
 */
@DisplayName("JWT 工具类测试")
class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private static final String TEST_SECRET = "test-secret-key-for-jwt-token-generation-at-least-32-chars-long";
    private static final Long TEST_USER_ID = 12345L;
    private static final String TEST_USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "accessExpire", 7200000L); // 2小时
        ReflectionTestUtils.setField(jwtUtils, "refreshExpire", 604800000L); // 7天
    }

    @Test
    @DisplayName("生成并解析 Access Token")
    void shouldGenerateAndParseAccessToken() {
        // 生成 Token
        String token = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);
        assertNotNull(token);
        assertTrue(token.length() > 0);

        // 解析 Token
        Claims claims = jwtUtils.parseToken(token);
        assertNotNull(claims);
        assertEquals(TEST_USER_ID, claims.get("userId", Long.class));
        assertEquals(TEST_USERNAME, claims.get("username", String.class));
        assertEquals(TokenType.ACCESS.value(), claims.get("type", String.class));
    }

    @Test
    @DisplayName("生成并解析 Refresh Token")
    void shouldGenerateAndParseRefreshToken() {
        // 生成 Token
        String token = jwtUtils.generateRefreshToken(TEST_USER_ID, TEST_USERNAME);
        assertNotNull(token);

        // 解析 Token
        Claims claims = jwtUtils.parseToken(token);
        assertNotNull(claims);
        assertEquals(TEST_USER_ID, claims.get("userId", Long.class));
        assertEquals(TokenType.REFRESH.value(), claims.get("type", String.class));
    }

    @Test
    @DisplayName("自定义 Token 生成")
    void shouldGenerateCustomToken() {
        String customJti = "custom-jti-123";
        long customExpire = 3600000L; // 1小时

        String token = jwtUtils.generateToken(TEST_USER_ID, TEST_USERNAME, TokenType.ACCESS, customJti, customExpire);
        assertNotNull(token);

        Claims claims = jwtUtils.parseToken(token);
        assertEquals(customJti, claims.getId());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("从 Token 获取用户ID")
    void shouldGetUserIdFromToken() {
        String token = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);
        Long userId = jwtUtils.getUserIdFromToken(token);
        assertEquals(TEST_USER_ID, userId);
    }

    @Test
    @DisplayName("从 Token 获取 Token 类型")
    void shouldGetTokenTypeFromToken() {
        String accessToken = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);
        assertEquals(TokenType.ACCESS, jwtUtils.getTokenType(accessToken));

        String refreshToken = jwtUtils.generateRefreshToken(TEST_USER_ID, TEST_USERNAME);
        assertEquals(TokenType.REFRESH, jwtUtils.getTokenType(refreshToken));
    }

    @Test
    @DisplayName("解析无效的 Token 应抛出异常")
    void shouldThrowExceptionForInvalidToken() {
        String invalidToken = "invalid.token.here";
        assertThrows(JwtException.class, () -> jwtUtils.parseToken(invalidToken));
    }

    @Test
    @DisplayName("解析被篡改的 Token 应抛出异常")
    void shouldThrowExceptionForTamperedToken() {
        String token = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";
        assertThrows(JwtException.class, () -> jwtUtils.parseToken(tamperedToken));
    }

    @Test
    @DisplayName("不同密钥生成的 Token 不应互相解析")
    void shouldNotParseTokenWithDifferentSecret() {
        String token = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);

        // 创建另一个使用不同密钥的 JwtUtils
        JwtUtils otherJwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(otherJwtUtils, "secret", "different-secret-key-for-testing-only-123");
        ReflectionTestUtils.setField(otherJwtUtils, "accessExpire", 7200000L);
        ReflectionTestUtils.setField(otherJwtUtils, "refreshExpire", 604800000L);

        assertThrows(JwtException.class, () -> otherJwtUtils.parseToken(token));
    }

    @Test
    @DisplayName("Token 应包含正确的 JTI")
    void shouldContainJti() {
        String token = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);
        Claims claims = jwtUtils.parseToken(token);
        assertNotNull(claims.getId());
        assertTrue(claims.getId().length() > 0);
    }

    @Test
    @DisplayName("不同 Token 应有不同的 JTI")
    void shouldGenerateDifferentJtiForEachToken() {
        String token1 = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);
        String token2 = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);

        Claims claims1 = jwtUtils.parseToken(token1);
        Claims claims2 = jwtUtils.parseToken(token2);

        assertNotEquals(claims1.getId(), claims2.getId());
    }

    @Test
    @DisplayName("Token 应包含颁发时间和过期时间")
    void shouldContainIssuedAndExpirationTime() {
        String token = jwtUtils.generateAccessToken(TEST_USER_ID, TEST_USERNAME);
        Claims claims = jwtUtils.parseToken(token);

        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }
}
