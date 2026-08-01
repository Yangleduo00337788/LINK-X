package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "活跃时段热力图")
public class AdminActivityHeatmapVO {

    @Schema(description = "指标：logins / messages")
    private String metric;

    @Schema(description = "统计天数")
    private Integer days;

    @Schema(description = "单元格最大值")
    private Long maxValue;

    @Schema(description = "区间合计")
    private Long total;

    @Schema(description = "单元格：[weekday(0=周一..6=周日), hour(0-23), count]")
    private List<List<Long>> cells;
}
