package com.linkx.server.util;


/**
 * 作者：yangleduo
 */
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 管理端 API AES-256-GCM 加解密（与 HMAC 签名密钥复用，32 字节）。
 */
public final class ApiEncryptUtils {

    public static final String HEADER_CONTENT_ENCRYPTED = "X-LinkX-Content-Encrypted";
    public static final String HEADER_ENCRYPTED_QUERY = "X-LinkX-Encrypted-Query";

    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ApiEncryptUtils() {
    }

    public static boolean isEncryptedRequest(String header) {
        return "1".equals(header != null ? header.trim() : null);
    }

    public static String encryptToBase64(byte[] key, byte[] plaintext) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext == null ? new byte[0] : plaintext);
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES encrypt failed", e);
        }
    }

    public static String encryptUtf8ToBase64(byte[] key, String plaintext) {
        return encryptToBase64(key, plaintext == null ? new byte[0] : plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decryptFromBase64(byte[] key, String base64) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
        if (!StringUtils.hasText(base64)) {
            throw new IllegalArgumentException("empty ciphertext");
        }
        byte[] combined = Base64.getDecoder().decode(base64.trim());
        if (combined.length <= IV_LENGTH) {
            throw new IllegalArgumentException("invalid ciphertext length");
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        byte[] ciphertext = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("AES decrypt failed", e);
        }
    }

    public static String decryptUtf8FromBase64(byte[] key, String base64) {
        return new String(decryptFromBase64(key, base64), StandardCharsets.UTF_8);
    }

    /**
     * 请求体为 JSON 字符串字面量（{@code "base64..."}）或裸 base64。
     */
    public static String unwrapEncryptedBody(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return "";
        }
        String trimmed = rawBody.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
