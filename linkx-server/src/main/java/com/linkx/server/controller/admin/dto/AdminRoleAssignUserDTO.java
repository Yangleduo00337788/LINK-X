package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "角色绑定用户")
public class AdminRoleAssignUserDTO {

    @Schema(description = "用户 ID 列表（全量覆盖）")
    @NotNull
    private List<Long> userIds;
}
