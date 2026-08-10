package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理员登录请求")
public class AdminLoginDTO {

    @Schema(description = "管理员账号", example = "admin")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度为 4-32 个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @Schema(description = "登录密码")
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度为 8-64 个字符")
    private String password;

    @Schema(description = "验证码ID")
    private String captchaId;

    @Schema(description = "验证码内容")
    private String captchaCode;
}
