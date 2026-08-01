package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户列表查询")
public class AdminUserQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "部门 ID")
    private Long deptId;
}
