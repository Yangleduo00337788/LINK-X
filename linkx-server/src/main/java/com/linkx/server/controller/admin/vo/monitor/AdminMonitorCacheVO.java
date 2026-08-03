package com.linkx.server.controller.admin.vo.monitor;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AdminMonitorCacheVO {
    private LocalDateTime refreshedAt;
    private long usedMemoryBytes;
    private long maxMemoryBytes;
    private double memoryUsagePercent;
    private long connectedClients;
    private double hitRatePercent;
    private double qps;
    private String redisVersion;
    private Map<String, String> info;
    private AdminMonitorTrendVO memoryTrend;
    private AdminMonitorTrendVO qpsTrend;
    private AdminMonitorTrendVO hitRateTrend;
    private AdminMonitorTrendVO connectionsTrend;
}
