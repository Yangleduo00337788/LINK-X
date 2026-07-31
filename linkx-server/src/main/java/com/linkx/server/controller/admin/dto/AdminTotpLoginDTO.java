package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理端 TOTP 登录验证")
public class AdminTotpLoginDTO {

    @NotBlank
    @Size(max = 128)
    private String challengeToken;

    @NotBlank
    @Size(min = 6, max = 8)
    private String code;
}
