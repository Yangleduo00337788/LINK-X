package com.linkx.server;

import com.linkx.server.config.LinkxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JWT Secret 启动校验测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Secret 启动校验测试")
class JwtSecretValidatorTest {

    @Mock
    private LinkxProperties linkxProperties;

    @Mock
    private LinkxProperties.Jwt jwt;

    @InjectMocks
    private JwtSecretValidator jwtSecretValidator;

    @BeforeEach
    void setUp() {
        when(linkxProperties.getJwt()).thenReturn(jwt);
    }

    @Test
    @DisplayName("有效的 JWT Secret 应通过验证")
    void shouldPassWithValidSecret() {
        String validSecret = "this-is-a-very-long-secret-key-for-jwt-generation-256-bits-long-key";
        when(jwt.getSecret()).thenReturn(validSecret);

        assertDoesNotThrow(() -> jwtSecretValidator.validateJwtSecret());
    }

    @Test
    @DisplayName("空的 JWT Secret 应抛出异常")
    void shouldThrowExceptionWithEmptySecret() {
        when(jwt.getSecret()).thenReturn("");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtSecretValidator.validateJwtSecret());
        assertTrue(exception.getMessage().contains("JWT Secret 不能为空"));
    }

    @Test
    @DisplayName("null 的 JWT Secret 应抛出异常")
    void shouldThrowExceptionWithNullSecret() {
        when(jwt.getSecret()).thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtSecretValidator.validateJwtSecret());
        assertTrue(exception.getMessage().contains("JWT Secret 不能为空"));
    }

    @Test
    @DisplayName("太短的 JWT Secret 应抛出异常")
    void shouldThrowExceptionWithShortSecret() {
        String shortSecret = "short";
        when(jwt.getSecret()).thenReturn(shortSecret);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtSecretValidator.validateJwtSecret());
        assertTrue(exception.getMessage().contains("JWT Secret 长度不足"));
        assertTrue(exception.getMessage().contains("32"));
    }

    @Test
    @DisplayName("恰好 32 字节的 JWT Secret 应通过")
    void shouldPassWithExactly32BytesSecret() {
        String exact32Bytes = "12345678901234567890123456789012";
        assertEquals(32, exact32Bytes.getBytes().length);
        when(jwt.getSecret()).thenReturn(exact32Bytes);

        assertDoesNotThrow(() -> jwtSecretValidator.validateJwtSecret());
    }

    @Test
    @DisplayName("31 字节的 JWT Secret 应抛出异常")
    void shouldThrowExceptionWith31BytesSecret() {
        String thirtyOneBytes = "1234567890123456789012345678901";
        assertEquals(31, thirtyOneBytes.getBytes().length);
        when(jwt.getSecret()).thenReturn(thirtyOneBytes);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtSecretValidator.validateJwtSecret());
        assertTrue(exception.getMessage().contains("长度不足"));
    }
}
