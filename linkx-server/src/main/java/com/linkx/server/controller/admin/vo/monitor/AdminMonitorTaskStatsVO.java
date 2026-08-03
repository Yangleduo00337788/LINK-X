package com.linkx.server.controller.admin.vo.monitor;

import com.linkx.server.controller.admin.vo.AdminSnailJobTaskVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminMonitorTaskStatsVO {
    private LocalDateTime refreshedAt;
    private boolean monitorAvailable;
    private int totalTasks;
    private int registeredTasks;
    private int enabledTasks;
    private int failedTasks;
    private long successBatches;
    private long failedBatches;
    private double successRatePercent;
    private List<AdminMonitorNamedValueVO> statusDistribution;
    private AdminMonitorTrendVO dailyTrend;
    private List<AdminSnailJobTaskVO> tasks;
}
