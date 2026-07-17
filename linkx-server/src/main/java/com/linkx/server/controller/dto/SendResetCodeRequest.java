package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送密码重置验证码请求
 */
@Data
public class SendResetCodeRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;
}
