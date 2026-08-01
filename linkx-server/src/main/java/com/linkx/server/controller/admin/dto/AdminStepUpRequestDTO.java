package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发起二次验证（发邮箱/短信验证码）")
public class AdminStepUpRequestDTO {

    @Schema(description = "验证方式：totp / email / sms", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String method;

    @Schema(description = "动作标识，如 admin:user:ban", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String action;
}
