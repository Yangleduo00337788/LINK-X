package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "TOTP 绑定准备信息")
public class AdminTotpSetupVO {

    @Schema(description = "Base32 密钥，可手动录入认证器")
    private String secret;

    @Schema(description = "otpauth URI，用于生成二维码")
    private String otpauthUri;
}
