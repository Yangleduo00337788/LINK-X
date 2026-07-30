package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "密码策略配置更新（管理端 / 客户端共用）")
public class PasswordSettingUpdateDTO {

    @NotNull
    @Min(4)
    @Max(128)
    @Schema(description = "密码最小长度")
    private Integer minLength;

    @NotNull
    @Min(4)
    @Max(128)
    @Schema(description = "密码最大长度")
    private Integer maxLength;

    @NotNull
    @Schema(description = "是否必须同时包含大小写字母")
    private Boolean requireUpperLower;

    @NotNull
    @Schema(description = "是否必须包含数字")
    private Boolean requireDigit;

    @NotNull
    @Schema(description = "是否必须包含特殊字符")
    private Boolean requireSpecial;
}
