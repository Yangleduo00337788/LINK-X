package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "权限点")
public class AdminPermissionVO {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private String resourceType;
    private String resourcePath;
    private String description;
    private Integer status;
}
