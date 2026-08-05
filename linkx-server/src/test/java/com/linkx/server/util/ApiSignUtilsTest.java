package com.linkx.server.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApiSignUtils")
class ApiSignUtilsTest {

    @Test
    @DisplayName("buildPayload 与 HMAC 验签一致")
    void signAndVerify() {
        byte[] key = ApiSignUtils.hexToBytes("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        String payload = ApiSignUtils.buildPayload(
                "1700000000000",
                "nonce123",
                "POST",
                "/admin/auth/me",
                ApiSignUtils.sha256Hex(""),
                ApiSignUtils.queryHashHex(""));
        String signature = ApiSignUtils.signHex(key, payload);
        assertTrue(ApiSignUtils.verifyHex(key, payload, signature));
    }

    @Test
    @DisplayName("normalizePath 去除 query")
    void normalizePath() {
        assertEquals("/admin/users", ApiSignUtils.normalizePath("/admin/users?page=1"));
        assertEquals("/admin/users", ApiSignUtils.normalizePath("admin/users"));
    }
}
