package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "测试忘记密码邮件")
public class TestForgotPasswordEmailDTO {

    @NotBlank
    @Email(message = "邮箱格式不正确")
    @Size(max = 128)
    @Schema(description = "测试收件邮箱")
    private String email;
}
