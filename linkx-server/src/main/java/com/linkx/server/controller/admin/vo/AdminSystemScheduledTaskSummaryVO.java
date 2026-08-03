package com.linkx.server.controller.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemScheduledTaskSummaryVO {

    private boolean monitorAvailable;
    private int totalTasks;
    private int registeredTasks;
    private int enabledTasks;
    private int failedTasks;
}
