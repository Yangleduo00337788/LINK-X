package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "用户设备强绑定开关")
public class AdminDeviceBindingDTO {

    @NotNull
    @Schema(description = "是否启用强绑定")
    private Boolean enabled;
}
