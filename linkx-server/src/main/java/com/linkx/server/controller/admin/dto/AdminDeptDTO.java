package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "部门创建/更新")
public class AdminDeptDTO {

    @Schema(description = "父部门ID，0=根")
    private Long parentId = 0L;

    @Schema(description = "部门名称")
    @NotBlank
    @Size(max = 64)
    private String name;

    @Schema(description = "排序")
    private Integer sortOrder = 0;

    @Schema(description = "状态 1启用 0停用")
    private Integer status = 1;
}
