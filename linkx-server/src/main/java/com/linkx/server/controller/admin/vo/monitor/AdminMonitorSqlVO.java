package com.linkx.server.controller.admin.vo.monitor;

import com.linkx.server.controller.admin.vo.AdminSystemConnectionPoolVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminMonitorSqlVO {
    private LocalDateTime refreshedAt;
    private AdminSystemConnectionPoolVO connectionPool;
    private long activeConnections;
    private long questionsTotal;
    private long slowQueries;
    private List<AdminMonitorSqlStatementVO> topStatements;
    private AdminMonitorTrendVO connectionTrend;
}
