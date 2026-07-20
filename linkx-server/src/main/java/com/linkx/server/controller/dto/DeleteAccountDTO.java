package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注销账号（需验证登录密码）
 */
@Data
public class DeleteAccountDTO {

    @NotBlank(message = "请输入登录密码以确认注销")
    private String password;
}
