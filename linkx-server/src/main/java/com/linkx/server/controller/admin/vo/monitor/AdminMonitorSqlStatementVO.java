package com.linkx.server.controller.admin.vo.monitor;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminMonitorSqlStatementVO {
    private String digest;
    private String sampleSql;
    private long execCount;
    private double avgLatencyMs;
    private double totalLatencyMs;
}
