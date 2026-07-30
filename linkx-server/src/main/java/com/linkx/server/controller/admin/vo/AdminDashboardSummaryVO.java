package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "仪表盘摘要")
public class AdminDashboardSummaryVO {

    private long totalUsers;
    private long activeUsers;
    private long onlineDevices;
    private long pendingFeedback;
    private long pendingReviews;
    private long riskEvents;
}
