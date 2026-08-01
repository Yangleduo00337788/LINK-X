package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "角色信息")
public class AdminRoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    /** 数据范围：1全部 2仅本人 3本部门及下级 */
    private Integer dataScope;
    private Date createTime;
    private Date updateTime;
}

