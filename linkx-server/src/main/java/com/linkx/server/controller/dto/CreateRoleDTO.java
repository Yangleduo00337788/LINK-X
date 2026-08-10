package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建角色请求 DTO。
 */
@Data
public class CreateRoleDTO {

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不超过 64")
    @Pattern(regexp = "^[a-zA-Z0-9_:]+$", message = "角色编码只能包含字母、数字、下划线、冒号")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不超过 64")
    private String roleName;

    @Size(max = 255, message = "角色描述长度不超过 255")
    private String description;
}
