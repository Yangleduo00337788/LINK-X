package com.linkx.server.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenType 枚举测试
 */
@DisplayName("TokenType 枚举测试")
class TokenTypeTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValuesTest {

        @Test
        @DisplayName("ACCESS枚举值应为access")
        void accessValue() {
            assertEquals("access", TokenType.ACCESS.value());
        }

        @Test
        @DisplayName("REFRESH枚举值应为refresh")
        void refreshValue() {
            assertEquals("refresh", TokenType.REFRESH.value());
        }

        @Test
        @DisplayName("应有且仅有两个枚举值")
        void enumValuesCount() {
            assertEquals(2, TokenType.values().length);
        }
    }

    @Nested
    @DisplayName("fromClaim 静态工厂方法测试")
    class FromClaimTest {

        @Test
        @DisplayName("access应返回ACCESS枚举")
        void accessClaim() {
            assertEquals(TokenType.ACCESS, TokenType.fromClaim("access"));
        }

        @Test
        @DisplayName("refresh应返回REFRESH枚举")
        void refreshClaim() {
            assertEquals(TokenType.REFRESH, TokenType.fromClaim("refresh"));
        }

        @Test
        @DisplayName("大小写不敏感 - ACCESS")
        void accessCaseInsensitive() {
            assertEquals(TokenType.ACCESS, TokenType.fromClaim("ACCESS"));
            assertEquals(TokenType.ACCESS, TokenType.fromClaim("Access"));
        }

        @Test
        @DisplayName("大小写不敏感 - REFRESH")
        void refreshCaseInsensitive() {
            assertEquals(TokenType.REFRESH, TokenType.fromClaim("REFRESH"));
            assertEquals(TokenType.REFRESH, TokenType.fromClaim("Refresh"));
        }

        @Test
        @DisplayName("null应默认返回ACCESS")
        void nullClaim_returnsAccess() {
            assertEquals(TokenType.ACCESS, TokenType.fromClaim(null));
        }

        @Test
        @DisplayName("未知值应抛出IllegalArgumentException")
        void unknownClaim_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> TokenType.fromClaim("unknown"));
        }

        @Test
        @DisplayName("空字符串应抛出IllegalArgumentException")
        void emptyClaim_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> TokenType.fromClaim(""));
        }

        @Test
        @DisplayName("空格应抛出IllegalArgumentException")
        void whitespaceClaim_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> TokenType.fromClaim(" "));
        }
    }

    @Nested
    @DisplayName("value()方法测试")
    class ValueMethodTest {

        @Test
        @DisplayName("所有枚举值的value方法应返回非空字符串")
        void allValuesReturnNonEmpty() {
            for (TokenType type : TokenType.values()) {
                assertNotNull(type.value());
                assertFalse(type.value().isEmpty());
            }
        }

        @Test
        @DisplayName("value方法返回的值应与fromClaim兼容")
        void valueCompatibleWithFromClaim() {
            for (TokenType type : TokenType.values()) {
                assertEquals(type, TokenType.fromClaim(type.value()));
            }
        }
    }
}
