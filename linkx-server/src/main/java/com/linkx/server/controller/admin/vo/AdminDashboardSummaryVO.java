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
    private long pendingReviews;
    private long riskEvents;
}
