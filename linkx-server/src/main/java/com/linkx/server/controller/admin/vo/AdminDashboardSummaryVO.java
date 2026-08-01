package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "仪表盘摘要")
public class AdminDashboardSummaryVO {

    private long totalUsers;
    /** 兼容字段：等同于 WAU（近 7 日成功登录去重用户） */
    private long activeUsers;
    @Schema(description = "日活：近 1 日成功登录去重用户")
    private long dau;
    @Schema(description = "周活：近 7 日成功登录去重用户")
    private long wau;
    @Schema(description = "月活：近 30 日成功登录去重用户")
    private long mau;
    private long onlineDevices;
    private long pendingFeedback;
    @Schema(description = "超过 SLA 仍未处理的反馈")
    private long overdueFeedback;
    private long pendingReviews;
    @Schema(description = "待处理举报（source_type=report）")
    private long pendingReports;
    @Schema(description = "今日敏感词命中次数")
    private long todaySensitiveHits;
    @Schema(description = "今日风控拦截次数（限流触发）")
    private long todayRiskBlocks;
    private long riskEvents;
}
