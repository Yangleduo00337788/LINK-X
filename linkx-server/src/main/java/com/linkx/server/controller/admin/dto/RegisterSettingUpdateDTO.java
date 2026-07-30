package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "注册配置更新（客户端）")
public class RegisterSettingUpdateDTO {

    @NotNull
    @Schema(description = "客户端是否开放注册")
    private Boolean registerEnabled;

    @NotNull
    @Schema(description = "忘记密码邮箱验证是否启用")
    private Boolean forgotPasswordEmailEnabled;
}
