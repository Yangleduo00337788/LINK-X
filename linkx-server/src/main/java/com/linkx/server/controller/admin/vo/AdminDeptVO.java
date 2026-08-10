package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "部门节点")
public class AdminDeptVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private List<AdminDeptVO> children;
}
