package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "权限点创建/更新")
public class AdminPermissionDTO {

    @Schema(description = "权限编码，如 admin:user:list")
    @NotBlank
    @Size(max = 128)
    private String permissionCode;

    @Schema(description = "权限名称")
    @NotBlank
    @Size(max = 128)
    private String permissionName;

    @Schema(description = "资源类型：page / button / api")
    @Size(max = 32)
    private String resourceType;

    @Schema(description = "资源路径")
    @Size(max = 255)
    private String resourcePath;

    @Schema(description = "描述")
    @Size(max = 255)
    private String description;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;
}
