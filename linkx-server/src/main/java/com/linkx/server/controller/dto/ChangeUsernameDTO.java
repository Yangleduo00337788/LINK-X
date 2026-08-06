package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改 LinkX ID（登录用户名），需验证登录密码。
 */
@Data
public class ChangeUsernameDTO {

    @NotBlank(message = "LinkX ID 不能为空")
    @Size(min = 4, max = 32, message = "LinkX ID 长度为 4-32 个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "LinkX ID 只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "请输入登录密码以确认身份")
    private String password;
}
