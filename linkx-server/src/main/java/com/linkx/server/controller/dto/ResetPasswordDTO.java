package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    @NotBlank(message = "验证码ID不能为空")
    private String captchaId;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在8-64位之间")
    private String newPassword;
}
