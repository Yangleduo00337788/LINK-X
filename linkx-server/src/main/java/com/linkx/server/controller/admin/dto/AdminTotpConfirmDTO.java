package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理端 TOTP 确认启用")
public class AdminTotpConfirmDTO {

    @NotBlank
    @Size(min = 6, max = 8)
    private String code;

    @Schema(description = "登录挑战令牌（强制绑定流程时必填）")
    @Size(max = 128)
    private String challengeToken;
}
