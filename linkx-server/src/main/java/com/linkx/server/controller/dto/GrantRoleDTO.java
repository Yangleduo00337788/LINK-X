package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分配角色请求 DTO。
 * <p>
 * 用于批量或体感化分配场景（当前控制器走 path 变量，此 DTO 预留扩展）。
 * </p>
 */
@Data
public class GrantRoleDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}
