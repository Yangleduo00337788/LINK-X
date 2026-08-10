package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求（已登录用户）。
 * 验证码与当前账号绑定，由 token 验证身份，不再接受任意 username 防横向越权。
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 1, max = 128, message = "密码长度过长")
    private String newPassword;

    /** 图形验证码（CAPTCHA_ENABLED=true 时必填；关闭时由服务端跳过校验） */
    private String captchaCode;

    private String captchaId;
}
