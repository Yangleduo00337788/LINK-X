package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "BI 查询结果")
public class AdminBiQueryVO {

    private String metric;
    private String dimension;
    private Integer days;
    private List<String> labels;
    private List<AdminChartSeriesVO> series;
    private List<AdminChartSeriesVO> compareSeries;
    private Double compareTotalDeltaPct;
    private List<AdminStatisticBreakdownVO> breakdown;
    private AdminBiDrillTargetVO drillTarget;
}
