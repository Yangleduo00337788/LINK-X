package com.linkx.server.controller.admin.vo.monitor;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminMonitorServiceVO {
    private LocalDateTime refreshedAt;
    private String osName;
    private String osArch;
    private String hostName;
    private int availableProcessors;
    private double systemCpuLoadPercent;
    private double processCpuLoadPercent;
    private long systemTotalMemoryBytes;
    private long systemFreeMemoryBytes;
    private double systemMemoryUsagePercent;
    private long jvmHeapUsedBytes;
    private long jvmHeapMaxBytes;
    private double jvmHeapUsagePercent;
    private long jvmNonHeapUsedBytes;
    private int threadCount;
    private int peakThreadCount;
    private long gcCount;
    private long uptimeMs;
    private Instant startTime;
    private String javaVersion;
    private long diskTotalBytes;
    private long diskFreeBytes;
    private double diskUsagePercent;
    private String diskPath;
    private AdminMonitorTrendVO cpuTrend;
    private AdminMonitorTrendVO memoryTrend;
}
