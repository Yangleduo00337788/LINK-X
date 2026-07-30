package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "管理端配置更新")
public class AdminSideSettingUpdateDTO {

    @NotNull
    @Schema(description = "管理端登录验证码")
    private Boolean captchaEnabled;
}
