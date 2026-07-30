package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理端分页查询基类")
public class AdminPageQueryDTO {

    @Schema(description = "页码，从 1 开始")
    private Integer page = 1;

    @Schema(description = "每页条数")
    private Integer size = 20;

    @Schema(description = "关键词")
    private String keyword;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "开始时间，毫秒时间戳")
    private Long startTime;

    @Schema(description = "结束时间，毫秒时间戳")
    private Long endTime;

    @Schema(description = "排序字段")
    private String sortBy;

    @Schema(description = "排序方向 asc/desc")
    private String sortOrder;
}
