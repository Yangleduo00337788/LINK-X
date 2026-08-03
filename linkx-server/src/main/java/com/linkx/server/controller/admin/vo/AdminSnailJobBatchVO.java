package com.linkx.server.controller.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminSnailJobBatchVO {
    private Long id;
    private Long jobId;
    private String jobName;
    private String batchStatus;
    private LocalDateTime executionAt;
    private LocalDateTime createDt;
    private Integer operationReason;
    private Long durationMs;
}
