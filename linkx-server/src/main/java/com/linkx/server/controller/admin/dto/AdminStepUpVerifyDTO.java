package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "校验二次验证码并签发 step-up token")
public class AdminStepUpVerifyDTO {

    @Schema(description = "验证方式：totp / email", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String method;

    @Schema(description = "6 位验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String code;

    @Schema(description = "动作标识，如 admin:user:ban", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String action;
}
