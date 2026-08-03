package com.linkx.server.controller.admin.vo.monitor;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminMonitorApiStatsVO {
    private LocalDateTime refreshedAt;
    private long totalRequests;
    private long successRequests;
    private long failedRequests;
    private List<AdminMonitorNamedValueVO> methodDistribution;
    private List<AdminMonitorNamedValueVO> topPaths;
    private AdminMonitorTrendVO dailyTrend;
}
