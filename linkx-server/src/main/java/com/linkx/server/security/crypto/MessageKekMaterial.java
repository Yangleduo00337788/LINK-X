package com.linkx.server.security.crypto;


/**
 * 作者：yangleduo
 */
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * MESSAGE_KEK 材料解析为 AES-256 密钥字节。
 */
public final class MessageKekMaterial {

    private static final int AES_KEY_BYTES = 32;

    private MessageKekMaterial() {
    }

    public static byte[] toAesKeyBytes(String kekMaterial) {
        if (!StringUtils.hasText(kekMaterial)) {
            throw new IllegalStateException("MESSAGE_KEK 未配置");
        }
        byte[] raw = decodeMaterial(kekMaterial.trim());
        if (raw.length == AES_KEY_BYTES) {
            return raw;
        }
        return sha256(raw);
    }

    private static byte[] decodeMaterial(String kek) {
        try {
            byte[] decoded = Base64.getDecoder().decode(kek);
            if (decoded.length >= AES_KEY_BYTES) {
                if (decoded.length == AES_KEY_BYTES) {
                    return decoded;
                }
                byte[] out = new byte[AES_KEY_BYTES];
                System.arraycopy(decoded, 0, out, 0, AES_KEY_BYTES);
                return out;
            }
        } catch (IllegalArgumentException ignored) {
            // 非 Base64，按 UTF-8 明文处理
        }
        byte[] utf8 = kek.getBytes(StandardCharsets.UTF_8);
        if (utf8.length < AES_KEY_BYTES) {
            throw new IllegalStateException(
                    "MESSAGE_KEK 长度不足，请提供至少 32 字符或使用 openssl rand -base64 32 生成");
        }
        return utf8;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
