package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "角色创建/更新")
public class AdminRoleDTO {

    @Schema(description = "角色编码")
    @NotBlank
    @Size(max = 64)
    private String roleCode;

    @Schema(description = "角色名称")
    @NotBlank
    @Size(max = 64)
    private String roleName;

    @Schema(description = "描述")
    @Size(max = 255)
    private String description;

    @Schema(description = "状态 1启用 0停用")
    private Integer status;
}
