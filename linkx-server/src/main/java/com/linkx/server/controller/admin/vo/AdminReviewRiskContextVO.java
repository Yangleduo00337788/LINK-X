package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "审核任务风险上下文")
public class AdminReviewRiskContextVO {

    @Schema(description = "综合风险分 0-100")
    private Integer riskScore;

    @Schema(description = "综合风险等级 low/medium/high/critical")
    private String computedRiskLevel;

    @Schema(description = "风险因子说明")
    private List<String> riskFactors;

    @Schema(description = "24h 内风险事件数")
    private Long recentRiskEventCount24h;

    @Schema(description = "24h 内高风险事件数")
    private Long recentHighRiskCount24h;

    @Schema(description = "近期关联风险事件")
    private List<AdminRiskEventBriefVO> recentRiskEvents;
}
