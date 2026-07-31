package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "风控统计")
public class AdminStatisticRiskVO {

    private AdminTrendVO trend;
    private List<AdminStatisticBreakdownVO> reviewStatusBreakdown;
    private long sensitiveHitsInRange;
    private long messageStormsInRange;
    private long pendingReviews;
}
