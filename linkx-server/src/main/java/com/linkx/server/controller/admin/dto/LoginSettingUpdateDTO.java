package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "登录配置更新（客户端 / 管理端）")
public class LoginSettingUpdateDTO {

    @NotNull
    @Valid
    @Schema(description = "客户端登录配置")
    private Side client;

    @NotNull
    @Valid
    @Schema(description = "管理端登录配置")
    private Side admin;

    @Data
    @Schema(description = "单侧登录配置")
    public static class Side {
        @NotNull
        @Schema(description = "是否开启图形验证码")
        private Boolean captchaEnabled;

        @NotNull
        @Min(1)
        @Max(100)
        @Schema(description = "登录失败最大重试次数，超出后自动禁用")
        private Integer maxAttempts;

        @NotNull
        @Min(1)
        @Max(24 * 60)
        @Schema(description = "自动封禁时长（分钟），到期自动解封")
        private Integer lockDurationMinutes;

        @Schema(description = "是否强制开启 TOTP 2FA（仅管理端侧生效）")
        private Boolean totpRequired;
    }
}
