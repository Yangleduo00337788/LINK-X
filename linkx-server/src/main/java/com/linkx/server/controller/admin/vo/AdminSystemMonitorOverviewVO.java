package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "系统监控总览")
public class AdminSystemMonitorOverviewVO {

    private LocalDateTime refreshedAt;
    private String applicationName;
    private String activeProfile;
    private String schemaName;
    private AdminSystemRuntimeVO runtime;
    private List<AdminSystemDependencyVO> dependencies;
    private AdminSystemConnectionPoolVO connectionPool;
    private AdminSystemHttpMetricsVO http;
    private AdminSystemBusinessMetricsVO business;
    private AdminSystemScheduledTaskSummaryVO scheduledTasks;
    private AdminSystemStorageSummaryVO storage;
    private List<AdminSystemTableStatVO> tables;
    @Schema(description = "information_schema 行数估算说明")
    private Boolean rowCountApproximate;
}
