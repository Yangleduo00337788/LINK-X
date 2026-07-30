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
}
