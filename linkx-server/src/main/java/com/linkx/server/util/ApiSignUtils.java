package com.linkx.server.util;

import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理端 API 轻量 HMAC 签名（请求完整性校验，不加密响应体）。
 */
public final class ApiSignUtils {

    public static final String HEADER_TIMESTAMP = "X-LinkX-Timestamp";
    public static final String HEADER_NONCE = "X-LinkX-Nonce";
    public static final String HEADER_SIGNATURE = "X-LinkX-Signature";

    private ApiSignUtils() {
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(data == null ? new byte[0] : data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    public static String buildPayload(
            String timestamp, String nonce, String method, String path, String bodyHash, String queryHash) {
        return String.join("\n",
                nullToEmpty(timestamp),
                nullToEmpty(nonce),
                nullToEmpty(method).toUpperCase(),
                normalizePath(path),
                nullToEmpty(bodyHash),
                nullToEmpty(queryHash));
    }

    public static String buildPayload(String timestamp, String nonce, String method, String path, String bodyHash) {
        return buildPayload(timestamp, nonce, method, path, bodyHash, "");
    }

    public static String queryHashHex(String queryMaterial) {
        return sha256Hex(queryMaterial == null ? "" : queryMaterial);
    }

    public static String signHex(byte[] apiSignKey, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSignKey, "HmacSHA256"));
            return bytesToHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    public static boolean verifyHex(byte[] apiSignKey, String payload, String signature) {
        if (!StringUtils.hasText(signature)) {
            return false;
        }
        String expected = signHex(apiSignKey, payload);
        return expected.equalsIgnoreCase(signature.trim());
    }

    public static byte[] hexToBytes(String hex) {
        String normalized = hex.trim();
        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("invalid hex length");
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(normalized.charAt(i * 2), 16);
            int lo = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex");
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    public static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        return p.isEmpty() ? "/" : p;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
