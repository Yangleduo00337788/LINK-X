package com.linkx.server.controller.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemHttpMetricsVO {

    private long totalRequests;
    private long clientErrorRequests;
    private long serverErrorRequests;
    private double avgLatencyMs;
    private double p95LatencyMs;
}
