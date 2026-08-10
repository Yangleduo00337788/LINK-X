package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "菜单详情")
public class AdminMenuVO {

    private Long id;
    private Long parentId;
    private String name;
    private String title;
    private String path;
    private String component;
    private String redirect;
    private String icon;
    private String menuType;
    private String permissionCode;
    private Integer sortOrder;
    private Integer hidden;
    private Integer status;
    private String remark;
    private Date createdAt;
    private Date updatedAt;
}
