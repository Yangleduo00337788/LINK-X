package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

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

    @Schema(description = "数据范围：1全部 2仅本人 3本部门及下级 4自定义组织")
    private Integer dataScope;

    @Schema(description = "自定义组织部门 ID 列表（dataScope=4 时必填）")
    private List<Long> deptIds;
}

