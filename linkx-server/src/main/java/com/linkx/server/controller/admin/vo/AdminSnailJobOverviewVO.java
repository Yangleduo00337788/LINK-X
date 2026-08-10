package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminSnailJobOverviewVO {
    private String adminConsoleUrl;
    private String clientGroup;
    private List<AdminSnailJobTaskVO> tasks;
    /** 是否可读取 SnailJob 库实时数据 */
    private Boolean monitorAvailable;
    private LocalDateTime refreshedAt;
    private Integer totalTasks;
    private Integer registeredTasks;
    private Integer enabledTasks;
    private Integer failedTasks;
}
