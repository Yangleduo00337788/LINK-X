package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "实时大屏数据")
public class AdminBigScreenVO {

    private Date refreshedAt;
    private long totalUsers;
    private long dau;
    private long onlineDevices;
    private long pendingFeedback;
    private long pendingReviews;
    private long todayMessages;
    private long todayLogins;
    private long todayRiskEvents;
    private List<TickerItem> tickers;
    @Schema(description = "KPI 近 7 日迷你趋势，key 与 KPI 字段一致")
    private Map<String, List<Long>> kpiTrends;

    @Data
    @Builder
    public static class TickerItem {
        private String type;
        private String title;
        private Long relatedId;
        private long ts;
    }
}
