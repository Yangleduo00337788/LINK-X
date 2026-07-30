package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "角色菜单授权")
public class AdminRoleAssignMenuDTO {

    @Schema(description = "菜单 ID 列表")
    @NotNull
    private List<Long> menuIds;
}
