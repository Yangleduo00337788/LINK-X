package com.linkx.server.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * RFC 6238 TOTP（HMAC-SHA1，30s，6 位），兼容 Google Authenticator / Microsoft Authenticator。
 */
public final class TotpUtils {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PERIOD_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int WINDOW = 1;

    private TotpUtils() {
    }

    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public static String otpAuthUri(String issuer, String account, String secret) {
        String safeIssuer = urlEncode(issuer == null || issuer.isBlank() ? "LinkX" : issuer.trim());
        String safeAccount = urlEncode(account == null ? "" : account.trim());
        return "otpauth://totp/" + safeIssuer + ":" + safeAccount
                + "?secret=" + secret
                + "&issuer=" + safeIssuer
                + "&algorithm=SHA1&digits=" + CODE_DIGITS + "&period=" + PERIOD_SECONDS;
    }

    public static boolean verify(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null) {
            return false;
        }
        String normalized = code.trim().replace(" ", "");
        if (!normalized.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        long counter = System.currentTimeMillis() / 1000 / PERIOD_SECONDS;
        byte[] key = decodeBase32(secret);
        for (int i = -WINDOW; i <= WINDOW; i++) {
            String expected = generateCode(key, counter + i);
            if (constantTimeEquals(expected, normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String generateCode(byte[] key, long counter) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    private static byte[] decodeBase32(String raw) {
        String secret = raw.trim().toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "");
        if (secret.isEmpty()) {
            throw new IllegalArgumentException("empty totp secret");
        }
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < secret.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(secret.charAt(i));
            if (val < 0) {
                throw new IllegalArgumentException("invalid totp secret");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}
