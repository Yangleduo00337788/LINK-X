package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 全局 BCrypt 密码编码器持有器（cost=12，线程安全）。
 * <p>
 * 统一替代 jbcrypt 0.4（已停更 7 年），使用 Spring Security 官方实现：
 *  - {@link #encode(String)} 对应 BCrypt.hashpw(p, BCrypt.gensalt(12))
 *  - {@link #matches(String, String)} 对应 BCrypt.checkpw(p, hash)
 * </p>
 * cost=12 与历史 jbcrypt 实现完全兼容，存量哈希可继续校验通过。
 */
public final class PasswordEncoderHolder {

    /** BCrypt cost factor，与历史 jbcrypt 保持一致 */
    private static final int COST = 12;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(COST);

    private PasswordEncoderHolder() {
    }

    /** 加密明文密码，返回 $2a$12$... 格式哈希 */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 校验明文密码与哈希是否匹配 */
    public static boolean matches(String rawPassword, String hashed) {
        if (rawPassword == null || hashed == null || hashed.isEmpty()) {
            return false;
        }
        return ENCODER.matches(rawPassword, hashed);
    }
}
