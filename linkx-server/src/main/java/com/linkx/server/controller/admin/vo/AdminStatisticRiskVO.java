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
    /** 审核吞吐：新建 vs 结案 */
    private AdminTrendVO reviewEfficiencyTrend;
    private List<AdminStatisticBreakdownVO> reviewStatusBreakdown;
    private long sensitiveHitsInRange;
    private long messageStormsInRange;
    private long loginLocksInRange;
    private long rateLimitsInRange;
    private long pendingReviews;
    /** 区间内已结案审核数 */
    private long resolvedReviewsInRange;
    /** 区间内平均处理时长（分钟，已结案） */
    private Double avgHandleMinutesInRange;
    /** 待审超过 24 小时 */
    private long pendingOver24h;
    /** 待审超过 72 小时 */
    private long pendingOver72h;
}
