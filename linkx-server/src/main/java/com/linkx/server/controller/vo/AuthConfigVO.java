package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录前可匿名读取的鉴权配置（供客户端决定是否展示验证码等）。
 */
@Data
@Builder
public class AuthConfigVO {
    /** 是否启用图形验证码（登录/注册校验） */
    private boolean captchaEnabled;
    /** 客户端是否开放注册 */
    private boolean registerEnabled;
    /** 忘记密码邮箱验证是否启用 */
    private boolean forgotPasswordEmailEnabled;
    /** 管理端是否强制要求 TOTP */
    private boolean totpRequired;
    /** 密码策略（管理端 / 客户端共用） */
    private PasswordPolicy passwordPolicy;
    /** 是否启用 API 请求签名（管理端） */
    private boolean apiSignEnabled;
    /** 是否启用 API 请求/响应体加密（管理端） */
    private boolean apiEncryptEnabled;
    /** 是否禁止前端调试 */
    private boolean disableFrontendDebug;

    @Data
    @Builder
    public static class PasswordPolicy {
        private int minLength;
        private int maxLength;
        private boolean requireUpperLower;
        private boolean requireDigit;
        private boolean requireSpecial;
    }
}
