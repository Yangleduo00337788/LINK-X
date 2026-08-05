package com.linkx.server.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiEncryptUtilsTest {

    private static final byte[] KEY = new byte[32];

    static {
        for (int i = 0; i < KEY.length; i++) {
            KEY[i] = (byte) i;
        }
    }

    @Test
    void roundTripUtf8() {
        String plain = "{\"id\":1,\"name\":\"测试\"}";
        String encrypted = ApiEncryptUtils.encryptUtf8ToBase64(KEY, plain);
        String decrypted = ApiEncryptUtils.decryptUtf8FromBase64(KEY, encrypted);
        assertEquals(plain, decrypted);
    }

    @Test
    void unwrapEncryptedBodyJsonString() {
        String base64 = ApiEncryptUtils.encryptUtf8ToBase64(KEY, "{}");
        String wrapped = "\"" + base64 + "\"";
        assertEquals(base64, ApiEncryptUtils.unwrapEncryptedBody(wrapped));
    }

    @Test
    void decryptRejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> ApiEncryptUtils.encryptUtf8ToBase64(new byte[16], "x"));
    }

    @Test
    void decryptRejectsTamperedCiphertext() {
        String encrypted = ApiEncryptUtils.encryptUtf8ToBase64(KEY, "hello");
        byte[] raw = java.util.Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01;
        String tampered = java.util.Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> ApiEncryptUtils.decryptUtf8FromBase64(KEY, tampered));
    }

    @Test
    void emptyPlaintextRoundTrip() {
        String encrypted = ApiEncryptUtils.encryptToBase64(KEY, new byte[0]);
        assertArrayEquals(new byte[0], ApiEncryptUtils.decryptFromBase64(KEY, encrypted));
        assertEquals("", new String(ApiEncryptUtils.decryptFromBase64(KEY, encrypted), StandardCharsets.UTF_8));
    }
}
