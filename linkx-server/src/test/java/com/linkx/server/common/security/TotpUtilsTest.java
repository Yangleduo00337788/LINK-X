package com.linkx.server.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TotpUtils RFC6238 工具测试")
class TotpUtilsTest {

    @Test
    @DisplayName("generateSecret 应返回非空 Base32")
    void generateSecret_returnsBase32() {
        String secret = TotpUtils.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.length() >= 16);
        assertTrue(secret.matches("[A-Z2-7]+"));
    }

    @Test
    @DisplayName("otpAuthUri 应包含 secret 与 issuer")
    void otpAuthUri_containsSecretAndIssuer() {
        String secret = TotpUtils.generateSecret();
        String uri = TotpUtils.otpAuthUri("LinkX", "user@test.com", secret);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=" + secret));
        assertTrue(uri.contains("issuer="));
    }

    @Test
    @DisplayName("otpAuthUri 空 issuer 应回退 LinkX")
    void otpAuthUri_blankIssuer_defaults() {
        String uri = TotpUtils.otpAuthUri("  ", "acct", TotpUtils.generateSecret());
        assertTrue(uri.contains("LinkX") || uri.toLowerCase().contains("linkx"));
    }

    @Test
    @DisplayName("verify 空参应返回 false")
    void verify_nullOrBlank_returnsFalse() {
        assertFalse(TotpUtils.verify(null, "123456"));
        assertFalse(TotpUtils.verify(" ", "123456"));
        assertFalse(TotpUtils.verify(TotpUtils.generateSecret(), null));
        assertFalse(TotpUtils.verify(TotpUtils.generateSecret(), "abcdef"));
        assertFalse(TotpUtils.verify(TotpUtils.generateSecret(), "12345"));
    }

    @Test
    @DisplayName("verify 错误码应返回 false")
    void verify_wrongCode_returnsFalse() {
        String secret = TotpUtils.generateSecret();
        assertFalse(TotpUtils.verify(secret, "000000"));
    }
}
