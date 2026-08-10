package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AdminSystemRuntimeVO {

    private long uptimeMs;
    private Instant startTime;
    private String javaVersion;
    private String osName;
    private String osArch;
    private int availableProcessors;
    private long heapUsedBytes;
    private long heapMaxBytes;
    private double heapUsagePercent;
    private long nonHeapUsedBytes;
    private int threadCount;
    private int peakThreadCount;
    private long gcCount;
}
