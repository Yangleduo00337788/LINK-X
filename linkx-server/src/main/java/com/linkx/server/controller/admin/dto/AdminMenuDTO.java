package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "菜单创建/更新")
public class AdminMenuDTO {

    @Schema(description = "父菜单ID")
    private Long parentId = 0L;

    @Schema(description = "菜单标识")
    @NotBlank
    @Size(max = 64)
    private String name;

    @Schema(description = "显示名称")
    @NotBlank
    @Size(max = 64)
    private String title;

    @Schema(description = "路由路径")
    @NotBlank
    @Size(max = 255)
    private String path;

    @Schema(description = "组件路径")
    @Size(max = 255)
    private String component;

    @Schema(description = "重定向")
    @Size(max = 255)
    private String redirect;

    @Schema(description = "图标")
    @Size(max = 64)
    private String icon;

    @Schema(description = "类型 dir/menu/button/api")
    @NotBlank
    private String menuType;

    @Schema(description = "权限码")
    @Size(max = 128)
    private String permissionCode;

    @Schema(description = "排序")
    private Integer sortOrder = 0;

    @Schema(description = "是否隐藏")
    private Integer hidden = 0;

    @Schema(description = "状态")
    private Integer status = 1;

    @Schema(description = "备注")
    @Size(max = 255)
    private String remark;
}
