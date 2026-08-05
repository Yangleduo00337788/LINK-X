package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "安全配置更新（管理端）")
public class SecuritySettingUpdateDTO {

    @NotNull
    @Schema(description = "是否禁止前端调试（开发者工具）")
    private Boolean disableFrontendDebug;

    @Schema(description = "是否启用 API 请求签名")
    private Boolean apiSignEnabled;

    @Schema(description = "是否启用 API 请求/响应体加密")
    private Boolean apiEncryptEnabled;
}
