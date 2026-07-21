package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新群成员角色（设为 / 取消管理员）
 */
@Data
public class UpdateMemberRoleDTO {

    /**
     * 目标角色：admin（设为管理员）或 member（取消管理员）
     */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "admin|member", message = "角色只能是 admin 或 member")
    private String role;
}
