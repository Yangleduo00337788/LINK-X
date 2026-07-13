package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendFriendRequestDTO {

    @NotBlank(message = "请输入对方账号")
    @Size(min = 4, max = 32, message = "账号长度为 4-32 个字符")
    private String username;

    @Size(max = 255, message = "验证信息不能超过255字符")
    private String message;
}
