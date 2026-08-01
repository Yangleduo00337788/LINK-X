package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Schema(description = "菜单树节点")
public class AdminMenuTreeVO {

    private Long id;
    private Long parentId;
    private String name;
    private String title;
    private String path;
    private String component;
    private String redirect;
    private String icon;
    private String type;
    private String permission;
    private Integer sort;
    private Boolean visible;
    private Integer status;
    @Builder.Default
    private List<AdminMenuTreeVO> children = new ArrayList<>();
}
