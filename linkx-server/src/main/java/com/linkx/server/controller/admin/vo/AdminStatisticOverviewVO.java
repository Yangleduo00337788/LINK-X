package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "统计总览")
public class AdminStatisticOverviewVO {

    private long totalUsers;
    private long activeUsers;
    private long onlineDevices;
    private long pendingFeedback;
    private long pendingReviews;
    private long riskEvents;
    private long todayNewUsers;
    private long todayMessages;
    private long todayLogins;
    private long totalMessages;
    private long totalUploads;
    private long closedFeedback;
}
