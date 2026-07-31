package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "趋势图数据")
public class AdminTrendVO {

    @Schema(description = "横轴标签，如 MM-dd")
    private List<String> labels;

    @Schema(description = "数据序列")
    private List<AdminChartSeriesVO> series;
}
