package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 API 签名密钥与加密密钥独立派生，避免跨用途密钥复用。
 */
class JwtUtilsDeriveApiKeyTest {

    private static final String SECRET =
            "unit-est-01fd7a8b9c2d3e4f5a6b7c8d9e0f1a2b";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtils = new JwtUtils();
        setField(jwtUtils, "secret", SECRET);
        setField(jwtUtils, "accessExpire", 7200000L);
        setField(jwtUtils, "refreshExpire", 604800000L);
    }

    @Test
    void signKey_and_encryptKey_are_distinct_forSameJti() {
        String jti = "jti-session-1";
        String signKey = jwtUtils.deriveApiSignKeyHex(jti);
        String encryptKey = jwtUtils.deriveApiEncryptKeyHex(jti);
        assertNotEquals(signKey, encryptKey,
                "签名密钥与加密密钥不得复用同一派生值");
    }

    @Test
    void derivedKeys_are_deterministic() {
        String jti = "jti-stable";
        assertEquals(jwtUtils.deriveApiSignKeyHex(jti), jwtUtils.deriveApiSignKeyHex(jti));
        assertEquals(jwtUtils.deriveApiEncryptKeyHex(jti), jwtUtils.deriveApiEncryptKeyHex(jti));
    }

    @Test
    void derivedKeys_differ_across_jti() {
        String signA = jwtUtils.deriveApiSignKeyHex("jti-A");
        String signB = jwtUtils.deriveApiSignKeyHex("jti-B");
        assertNotEquals(signA, signB);

        String encA = jwtUtils.deriveApiEncryptKeyHex("jti-A");
        String encB = jwtUtils.deriveApiEncryptKeyHex("jti-B");
        assertNotEquals(encA, encB);
    }

    @Test
    void derivedKeys_are_32BytesHex_escape128BitSplit() {
        // 32 字节 => hex 恰好 64 位，避免被截断或误当 16 字节使用
        String signKey = jwtUtils.deriveApiSignKeyHex("jti");
        String encryptKey = jwtUtils.deriveApiEncryptKeyHex("jti");
        assertEquals(64, signKey.length());
        assertEquals(64, encryptKey.length());
        assertTrue(signKey.matches("[0-9a-f]{64}"), "签名密钥应为 64 位 hex");
        assertTrue(encryptKey.matches("[0-9a-f]{64}"), "加密密钥应为 64 位 hex");
    }

    @Test
    void blank_jti_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.deriveApiSignKeyHex(""));
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.deriveApiSignKeyHex(null));
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.deriveApiEncryptKeyHex("  "));
    }

    @Test
    void keys_derivedFromAccessTokenJti_are_distinct() {
        String token = jwtUtils.generateAccessToken(1001L, "alice");
        String jti = jwtUtils.getJtiFromToken(token);
        assertNotEquals(jwtUtils.deriveApiSignKeyHex(jti), jwtUtils.deriveApiEncryptKeyHex(jti));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = JwtUtils.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}