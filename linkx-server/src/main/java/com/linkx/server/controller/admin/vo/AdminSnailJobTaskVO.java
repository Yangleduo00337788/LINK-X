package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminSnailJobTaskVO {
    private Long jobId;
    private String executorName;
    private String jobName;
    private String description;
    private String triggerType;
    private String triggerInterval;
    private Integer executorTimeoutSeconds;
    /** 0=停用 1=启用 */
    private Integer jobStatus;
    private LocalDateTime nextTriggerAt;
    private Long lastBatchId;
    private String lastBatchStatus;
    private LocalDateTime lastExecutionAt;
    private Long lastDurationMs;
    /** SnailJob 中是否已注册 */
    private Boolean registered;
}
